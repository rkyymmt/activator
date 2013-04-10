package snap

import akka.actor._
import com.typesafe.sbtchild._
import play.api.libs.json._
import JsonHelper._

/**
 * The point of this actor is to separate the "event" replies
 * from the "response" reply and only reply with the "response"
 * while forwarding the events to our socket.
 * if (fireAndForget) then we send RequestReceivedEvent as the reply
 * to the requestor, otherwise we send the Response as the reply.
 *
 * @param taskId - The task id that's running.
 * @param taskActor - An sbtchild actor or proxy.
 * @param eventHandler  - What to do when an event comes.
 * @param respondWhenReceived - Whether or not we respond when we receive a done notification.
 */
class RequestManagerActor(taskId: String, taskActor: ActorRef, respondWhenReceived: Boolean)(eventHandler: JsObject => Unit) extends Actor with ActorLogging {
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
      eventHandler(JsObject(Seq("taskId" -> JsString(taskId),
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
    // TODO - Safer hackery
    val json = scalaJsonToPlayJson(protocol.Message.JsonRepresentationOfMessage.toJson(event))
    eventHandler(JsObject(Seq("taskId" -> JsString(taskId), "event" -> json)))
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