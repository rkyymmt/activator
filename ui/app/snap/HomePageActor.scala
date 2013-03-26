package snap

import akka.actor._
import play.api.libs.json._
import play.api.libs.iteratee.Concurrent
import java.io.File
import akka.pattern.pipe

// THE API for the HomePage actor.
case class OpenExistingApplication(location: String)
object OpenExistingApplication {
  def unapply(in: JsValue): Option[OpenExistingApplication] =
    try if ((in \ "request").as[String] == "OpenExistingApplication")
      Some(OpenExistingApplication((in \ "location").as[String]))
    else None
    catch {
      case e: JsResultException => None
    }
}
case class CreateNewApplication(location: String, templateId: String)
object CreateNewApplication {
  def unapply(in: JsValue): Option[CreateNewApplication] =
    try if ((in \ "request").as[String] == "CreateNewApplication")
      Some(CreateNewApplication(
        (in \ "location").as[String],
        (in \ "template").asOpt[String] getOrElse ""))
    else None
    catch {
      case e: JsResultException => None
    }
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
case class AddHomePageSocket(channel: Concurrent.Channel[JsValue])
case class Respond(json: JsValue)

// This actor controls home page actions and ensures we can survive past timeouts...
// TODO - Split this between something that handles the websockets
// and another actor that does the actions and is testable.
class HomePageActor extends Actor with ActorLogging {
  var socket: Option[Concurrent.Channel[JsValue]] = None
  def receive: Receive = {
    case Respond(json) => notifyListeners(json)
    case in: JsValue => in match {
      case OpenExistingApplication(msg) => openExistingApplication(msg.location)
      case CreateNewApplication(msg) => createNewApplication(msg.location, msg.templateId)
      case _ =>
        // TODO - Send error...
        log.error(s"HomeActor: received unknown msg: " + in)
    }
    case AddHomePageSocket(channel) => socket = Some(channel)
  }

  // Goes off and tries to create/load an application.
  def createNewApplication(location: String, template: String): Unit = {
    import context.dispatcher
    val appLocation = new java.io.File(location)
    // a chance of knowing what the error is.
    val installed: ProcessResult[File] =
      //TODO - Store template cache somehwere better...
      snap.cache.Actions.cloneTemplate(
        controllers.api.Templates.templateCache,
        template,
        appLocation) map (_ => appLocation)

    loadApplicationAndSendResponse(installed)
  }

  // Goes off and tries to open an application, responding with
  // whether or not we were successful to this actor.
  def openExistingApplication(location: String): Unit = {
    log.debug(s"Looking for existing application at: $location")
    // TODO - Ensure timeout is ok...
    val file = snap.Validating(new File(location)).validate(
      snap.Validation.fileExists,
      snap.Validation.isDirectory)
    loadApplicationAndSendResponse(file)
  }

  // helper method that given a validated file, will try to load
  // the application id and return an appropriate response.
  private def loadApplicationAndSendResponse(file: ProcessResult[File]) = {
    import context.dispatcher
    val id = file flatMapNested AppManager.loadAppIdFromLocation
    val response = id map {
      case snap.ProcessSuccess(id) =>
        log.debug(s"HomeActor: Found application id: $id")
        RedirectToApplication(id)
      // TODO - Return with form and flash errors?
      case snap.ProcessFailure(errors) =>
        log.debug(s"HomeActor: Failed to find application: ${errors map (_.msg) mkString "\n\t"}")
        BadRequest("OpenExistingApplication", errors map (_.msg))
    } map Respond.apply
    pipe(response) to self
  }

  def notifyListeners(msg: JsValue): Unit = {
    log.debug(s"HomeActor: Telling socket: $msg")
    socket foreach (_ push msg)
  }

  override def postStop(): Unit = {
    log.debug("Stopping homeActor.")
    socket foreach (_.eofAndEnd)
  }
}