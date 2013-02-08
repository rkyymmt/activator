package controllers.api

import play.api.mvc.{ Action, Controller }
import play.api.libs.json._
import play.api.Play
import com.typesafe.sbtchild._
import play.api.mvc._
import java.util.UUID
import snap.AppManager
import akka.pattern._
import akka.actor._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.parsing.json.JSONType
import scala.util.parsing.json.JSONObject
import scala.util.parsing.json.JSONArray
import scala.math.BigDecimal
import play.Logger
import scala.concurrent.Future
import snap.GetTaskActor
import snap.TaskActorReply
import snap.NotifyWebSocket
import java.net.URLEncoder

object Sbt extends Controller {
  implicit val timeout = Timeout(300.seconds)

  // The point of this actor is to separate the "event" replies
  // from the "response" reply and only reply with the "response"
  // while forwarding the events to our socket
  private class EventsForRequestActor(app: snap.App, taskId: String, taskActor: ActorRef) extends Actor with ActorLogging {
    override def receive = awaitingRequest

    def awaitingRequest: Receive = {
      case req: protocol.Request =>
        taskActor ! req
        context.become(awaitingResponse(sender))
    }

    def awaitingResponse(requestor: ActorRef): Receive = {
      case event: protocol.Event =>
        log.debug("event: {}", event)
        val json = scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(event))
        app.actor ! NotifyWebSocket(JsObject(Seq("taskId" -> JsString(taskId), "event" -> json)))
      case response: protocol.Response =>
        requestor ! response
        self ! PoisonPill
    }
  }

  def sendRequestGettingEvents(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request): Future[protocol.Response] = {
    val actor = factory.actorOf(Props(new EventsForRequestActor(app, taskId, taskActor)),
      name = "event-tee-" + URLEncoder.encode(taskId, "UTF-8"))
    (actor ? request) map {
      case response: protocol.Response => response
      case whatever => throw new RuntimeException("unexpected response: " + whatever)
    }
  }

  // Incoming JSON should be:
  //  { "appId" : appid, "description" : human-readable,
  //    "taskId" : uuid,
  //    "task" : json-as-we-define-in-sbtchild.protocol }
  // And the reply will be a message as defined in sbtchild.protocol
  def task() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskId = (json \ "taskId").as[String]
    val taskDescription = (json \ "description").as[String]
    val taskJson = (json \ "task")

    val taskMessage = protocol.Message.JsonRepresentationOfMessage.fromJson(playJsonToScalaJson(taskJson)) match {
      case req: protocol.Request => req
      case whatever => throw new RuntimeException("not a request: " + whatever)
    }

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      withTaskActor(taskId, taskDescription, app) { taskActor =>
        val taskFuture = sendRequestGettingEvents(snap.Akka.system, app, taskId, taskActor, taskMessage) map {
          case message: protocol.Message =>
            Ok(scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(message)))
        }
        taskFuture.onComplete { _ =>
          Logger.debug("Killing task actor")
          taskActor ! PoisonPill
        }
        taskFuture
      }
    }
    Async(resultFuture)
  }

  private def jsonAction(f: JsValue => Result): Action[AnyContent] = Action { request =>
    request.body.asJson.map({ json =>
      try f(json)
      catch {
        case e: Exception =>
          Logger.info("json action failed: " + e.getMessage(), e)
          BadRequest(e.getClass.getName + ": " + e.getMessage)
      }
    }).getOrElse(BadRequest("expecting JSON body"))
  }

  private def withTaskActor[T](taskId: String, taskDescription: String, app: snap.App)(body: ActorRef => Future[T]): Future[T] = {
    (app.actor ? GetTaskActor(taskId, taskDescription)) flatMap {
      case TaskActorReply(taskActor) => body(taskActor)
    }
  }

  private def playJsonToScalaJson(playJson: JsValue): JSONType = {
    def playJsonToScalaJsonValue(playJson: JsValue): Any = {
      playJson match {
        case JsBoolean(b) => b
        case JsNumber(n) => n
        case JsString(s) => s
        case JsNull => null
        case o: JsObject => playJsonToScalaJson(o)
        case a: JsArray => playJsonToScalaJson(a)
        case u: JsUndefined => throw new RuntimeException("undefined found in json")
      }
    }

    playJson match {
      case JsObject(list) =>
        JSONObject((list map { kv =>
          kv._1 -> playJsonToScalaJsonValue(kv._2)
        }).toMap)
      case JsArray(list) =>
        JSONArray(list.map(playJsonToScalaJsonValue).toList)
      case other =>
        throw new RuntimeException("only JSON 'containers' allowed here, not " + other.getClass)
    }
  }

  private def scalaJsonToPlayJson(scalaJson: JSONType): JsValue = {
    def scalaJsonToPlayJsonValue(scalaJson: Any): JsValue = {
      scalaJson match {
        // always check null first since it's an instance of everything
        case null => JsNull
        case o: JSONObject => scalaJsonToPlayJson(o)
        case a: JSONArray => scalaJsonToPlayJson(a)
        case b: Boolean => JsBoolean(b)
        case n: Double => JsNumber(BigDecimal(n))
        case n: Long => JsNumber(BigDecimal(n))
        case n: Int => JsNumber(BigDecimal(n))
        case s: String => JsString(s)
      }
    }

    scalaJson match {
      case JSONObject(m) =>
        JsObject(m.iterator.map(kv => (kv._1 -> scalaJsonToPlayJsonValue(kv._2))).toSeq)
      case JSONArray(list) =>
        JsArray(list.map(scalaJsonToPlayJsonValue))
      case other =>
        throw new RuntimeException("only JSON 'containers' allowed here, not " + other.getClass)
    }
  }
}
