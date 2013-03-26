package snap

import akka.actor._
import play.api.libs.json._
import play.api.libs.iteratee.Concurrent
import java.io.File
import akka.pattern.pipe

case class OpenExistingApplication(location: String)
object OpenExistingApplication {
  def unapply(in: JsValue): Option[OpenExistingApplication] =
    if ((in \ "request").as[String] == "OpenExistingApplication")
      Some(OpenExistingApplication((in \ "location").as[String]))
    else None
}

object RedirectToApplication {
  def apply(id: String): JsValue =
    JsObject(Seq(
      "response" -> JsString("RedirectToApplication"),
      "appId" -> JsString(id)))
}
object BadRequest {
  def apply(request: String, errors: Seq[String]): JsValue =
    JsObject(Seq(
      "response" -> JsString("BadRequest"),
      "errors" -> JsArray(errors map JsString.apply)))
}
case class AddSocket(channel: Concurrent.Channel[JsValue])
case class RemoveSocket(channel: Concurrent.Channel[JsValue])
case class Respond(json: JsValue)

// This actor controls home page actions and ensures we can survive past timeouts...
// TODO - Split this between something that handles the websockets
// and another actor that does the actions and is testable.
class HomePageActor extends Actor {
  val sockets = collection.mutable.ArrayBuffer.empty[Concurrent.Channel[JsValue]]
  def receive: Receive = {
    case Respond(json) => notifyListeners(json)
    case in: JsValue => in match {
      case OpenExistingApplication(msg) => openExistingApplication(msg.location)
      case _ =>
        // TODO - Send error...
        println(s"HomeActor: received unknown msg: " + in)
    }
    case AddSocket(channel) => sockets append channel
    case RemoveSocket(channel) => sockets remove (sockets indexOf channel)
  }

  // Goes off and tries to open an application, responding with
  // whether or not we were successful to this actor.
  def openExistingApplication(location: String): Unit = {
    println(s"Looking for existing application at: $location")
    // TODO - Ensure timeout is ok...
    val file = snap.Validating(new File(location)).validate(
      snap.Validation.fileExists,
      snap.Validation.isDirectory)
    import context.dispatcher
    val self = context.self
    val id = file flatMapNested AppManager.loadAppIdFromLocation
    val response = id map {
      case snap.ProcessSuccess(id) =>
        println(s"HomeActor: Found application id: $id")
        RedirectToApplication(id)
      // TODO - Return with form and flash errors?
      case snap.ProcessFailure(errors) =>
        println(s"HomeActor: Failed to find application: ${errors map (_.msg) mkString "\n\t"}")
        BadRequest("OpenExistingApplication", errors map (_.msg))
    } map Respond.apply
    pipe(response) to self
  }
  def notifyListeners(msg: JsValue): Unit = {
    println(s"HomeActor: Telling all sockets: $msg")
    sockets foreach (_ push msg)
  }

  override def postStop(): Unit = {
    sockets foreach (_.eofAndEnd)
    sockets.clear()
  }
}