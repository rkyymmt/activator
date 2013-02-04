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

object Sbt extends Controller {
  implicit val timeout = Timeout(300.seconds)

  // Incoming JSON should be:
  //  { "appId" : appid, "description" : human-readable,
  //    "task" : json-as-we-define-in-sbtchild.protocol }
  // And the reply will be a message as defined in sbtchild.protocol
  def task() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskDescription = (json \ "description").as[String]
    val taskJson = (json \ "task")

    val taskMessage = protocol.Message.JsonRepresentationOfMessage.fromJson(playJsonToScalaJson(taskJson))

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      withTaskActor(taskDescription, app) { taskActor =>
        val taskFuture = (taskActor ? taskMessage) map {
          case message: protocol.Message =>
            Ok(scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(message)))
        }
        taskFuture.onComplete(_ => taskActor ! PoisonPill)
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
          Logger.info("json action failed", e)
          BadRequest(e.getClass.getName + ": " + e.getMessage)
      }
    }).getOrElse(BadRequest("expecting JSON body"))
  }

  private def withTaskActor[T](taskDescription: String, app: snap.App)(body: ActorRef => Future[T]): Future[T] = {
    (app.actor ? GetTaskActor(taskDescription)) flatMap {
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
