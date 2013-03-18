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
import snap.UpdateSourceFiles

object Sbt extends Controller {
  implicit val timeout = snap.Akka.longTimeoutThatIsAProblem

  // The point of this actor is to separate the "event" replies
  // from the "response" reply and only reply with the "response"
  // while forwarding the events to our socket.
  // if (fireAndForget) then we send RequestReceivedEvent as the reply
  // to the requestor, otherwise we send the Response as the reply.
  private class RequestManagerActor(app: snap.App, taskId: String, taskActor: ActorRef, respondWhenReceived: Boolean) extends Actor with ActorLogging {
    // so postStop can be sure one is sent
    var needsReply: Option[ActorRef] = None
    var completed = false

    context.watch(taskActor)

    val handleTerminated: Receive = {
      case Terminated(ref) if ref == taskActor =>
        log.debug("taskActor died so killing RequestManagerActor")
        self ! PoisonPill
    }

    override def receive = awaitingRequest

    def awaitingRequest: Receive = handleTerminated orElse {
      case req: protocol.Request =>
        needsReply = Some(sender)
        taskActor ! req
        if (respondWhenReceived)
          context.become(awaitingRequestReceivedOrError(sender))
        else
          context.become(awaitingActualResponse(sender))
    }

    private def sendTaskComplete(response: protocol.Response): Unit = {
      if (!completed) {
        completed = true

        val responseJsonInEvent = if (respondWhenReceived) {
          // since the actual response isn't sent to the requestor,
          // it needs to go in the TaskComplete
          scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(response))
        } else {
          // don't need to put data in TaskComplete event; it might be
          // kind of large (for example a list of files), so just
          // send it to one place, to the requestor.
          JsObject(Nil)
        }

        app.actor ! NotifyWebSocket(JsObject(Seq("taskId" -> JsString(taskId),
          "event" -> JsObject(Seq(
            "type" -> JsString("TaskComplete"),
            "response" -> responseJsonInEvent)))))
      }
    }

    private def handleFinalResponse(response: protocol.Response): Unit = {
      sendTaskComplete(response)
      context.become(gotResponse)
      log.debug("killing self after receiving response")
      self ! PoisonPill
    }

    // response can be ErrorResponse, an actual response, or RequestReceivedEvent
    private def sendReply(requestor: ActorRef, response: protocol.Message): Unit = {
      requestor ! response

      needsReply = None
    }

    private def eventToSocket(event: protocol.Event): Unit = {
      log.debug("event: {}", event)
      val json = scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(event))
      app.actor ! NotifyWebSocket(JsObject(Seq("taskId" -> JsString(taskId), "event" -> json)))
    }

    // send back only actual responses (not RequestReceivedEvent)
    def awaitingActualResponse(requestor: ActorRef): Receive = handleTerminated orElse {
      case event: protocol.Event =>
        eventToSocket(event)

      case response: protocol.Response =>
        sendReply(requestor, response)
        handleFinalResponse(response)
    }

    // we need to send back ErrorResponse or RequestReceivedEvent
    def awaitingRequestReceivedOrError(requestor: ActorRef): Receive = handleTerminated orElse {
      case event: protocol.Event =>
        eventToSocket(event)
        event match {
          case protocol.RequestReceivedEvent =>
            sendReply(requestor, protocol.RequestReceivedEvent)
            context.become(awaitingFinalResponse)
          case _ => // nothing
        }

      case response: protocol.Response =>
        sendReply(requestor, response)
        handleFinalResponse(response)
    }

    // state after we get RequestReceivedEvent but before we get the
    // real response
    def awaitingFinalResponse(): Receive = handleTerminated orElse {
      case event: protocol.Event =>
        eventToSocket(event)
      case response: protocol.Response =>
        handleFinalResponse(response)
    }

    def gotResponse: Receive = handleTerminated orElse {
      case message: protocol.Message =>
        log.warning("Got a message after request should have been finished {}", message)
    }

    // this makes us a little less generic (taskActor can't be used for multiple requests)
    // but for now it's OK since we don't use taskActor for multiple requests.
    override def postStop = {
      val response = protocol.ErrorResponse("Failure prior to receiving response from sbt")
      for (requestor <- needsReply) {
        sendReply(requestor, response)
      }
      sendTaskComplete(response)

      log.debug("killing taskActor in postStop")
      taskActor ! PoisonPill
    }
  }

  def sendRequestGettingEvents(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request, fireAndForget: Boolean): Future[protocol.Message] = {
    val actor = factory.actorOf(Props(new RequestManagerActor(app, taskId, taskActor, fireAndForget)),
      name = "event-tee-" + URLEncoder.encode(taskId, "UTF-8"))
    (actor ? request) map {
      case message: protocol.Message => message
      case whatever => throw new RuntimeException("only expected messages here: " + whatever)
    }
  }

  def sendRequestGettingEventsAndResponse(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request): Future[protocol.Response] = {
    sendRequestGettingEvents(factory, app, taskId, taskActor, request, fireAndForget = false) map {
      case response: protocol.Response => response
      case whatever => throw new RuntimeException("unexpected response: " + whatever)
    }
  }

  def sendRequestGettingAckAndEvents(factory: ActorRefFactory, app: snap.App, taskId: String, taskActor: ActorRef, request: protocol.Request): Future[protocol.Message] = {
    sendRequestGettingEvents(factory, app, taskId, taskActor, request, fireAndForget = true) map {
      case protocol.RequestReceivedEvent => protocol.RequestReceivedEvent
      case response: protocol.ErrorResponse => response
      case whatever => throw new RuntimeException("unexpected response: " + whatever)
    }
  }

  // Incoming JSON should be:
  //  { "appId" : appid, "description" : human-readable,
  //    "taskId" : uuid,
  //    "task" : json-as-we-define-in-sbtchild.protocol }
  // And the reply will be RequestReceivedEvent or ErrorResponse
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
        sendRequestGettingAckAndEvents(snap.Akka.system, app, taskId, taskActor, taskMessage) map {
          case protocol.RequestReceivedEvent => protocol.RequestReceivedEvent
          case error: protocol.ErrorResponse => error
          case whatever => throw new RuntimeException("unexpected response: " + whatever)
        } map { message =>
          Ok(scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(message)))
        }
      }
    }
    Async(resultFuture)
  }

  // Incoming JSON { "appId" : appId, "taskId" : uuid }
  // reply is empty
  def killTask() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskId = (json \ "taskId").as[String]
    val resultFuture = AppManager.loadApp(appId) map { app =>
      app.actor ! snap.ForceStopTask(taskId)
      Ok(JsObject(Nil))
    }
    Async(resultFuture)
  }

  // Incoming JSON { "appId" : appId, "taskId" : uuid }
  // the request causes us to reload the sources from sbt and watch them.
  // This is only its own API (vs. "task" above) because we are
  // trying to avoid sending the potentially big list of source files
  // to the client and back. We still want the client to control when
  // we refresh the list, though.
  def watchSources() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val taskId = (json \ "taskId").as[String]

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      withTaskActor(taskId, "Finding sources to watch for changes", app) { taskActor =>
        sendRequestGettingEventsAndResponse(snap.Akka.system, app, taskId, taskActor,
          protocol.WatchTransitiveSourcesRequest(sendEvents = true)) map {
            case protocol.WatchTransitiveSourcesResponse(files) =>
              val filesSet = files.toSet
              Logger.debug(s"Sending app actor ${filesSet.size} source files")
              app.actor ! UpdateSourceFiles(filesSet)
              Ok(JsObject(Seq("type" -> JsString("WatchTransitiveSourcesResponse"),
                "count" -> JsNumber(filesSet.size))))
            case message: protocol.Message =>
              Ok(scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(message)))
          }
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
