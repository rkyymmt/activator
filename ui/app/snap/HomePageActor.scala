package snap

import akka.actor._
import play.api.libs.json._
import play.api.libs.iteratee.Concurrent
import java.io.File
import akka.pattern.pipe
import scala.util.control.NonFatal
import scala.concurrent.Future
import activator._

// THE API for the HomePage actor.
object HomePageActor {
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
  case class CreateNewApplication(location: String, templateId: String, projectName: Option[String])
  object CreateNewApplication {
    def unapply(in: JsValue): Option[CreateNewApplication] =
      try if ((in \ "request").as[String] == "CreateNewApplication")
        Some(CreateNewApplication(
          (in \ "location").as[String],
          (in \ "template").asOpt[String] getOrElse "",
          (in \ "name").asOpt[String]))
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
  object Status {
    def apply(info: String): JsValue =
      JsObject(Seq(
        "response" -> JsString("Status"),
        "info" -> JsString(info)))
  }
  case class Respond(json: JsValue)

  object LicenseAccepted {
    def apply(): JsValue =
      JsObject(Seq(
        "response" -> JsString("LicenseAccepted")))
    def unapply(in: JsValue): Boolean =
      try {
        if ((in \ "request").as[String] == "LicenseAccepted") true
        else false
      } catch {
        case e: JsResultException => false
      }
  }
}
class HomePageActor extends WebSocketActor[JsValue] with ActorLogging {

  AppManager.registerKeepAlive(self)

  import HomePageActor._
  override def onMessage(json: JsValue): Unit = json match {
    case WebSocketActor.Ping(ping) => produce(WebSocketActor.Pong(ping.cookie))
    case OpenExistingApplication(msg) => openExistingApplication(msg.location)
    case CreateNewApplication(msg) => createNewApplication(msg.location, msg.templateId, msg.projectName)
    case _ =>
      log.error(s"HomeActor: received unknown msg: $json")
      produce(BadRequest(json.toString, Seq("Could not parse JSON for request")))
  }

  override def subReceive: Receive = {
    case Respond(json) => produce(json)
  }

  // Goes off and tries to create/load an application.
  def createNewApplication(location: String, template: String, projectName: Option[String]): Unit = {
    import context.dispatcher
    val appLocation = new java.io.File(location)
    // a chance of knowing what the error is.
    val installed: Future[ProcessResult[File]] =
      controllers.api.Templates.doCloneTemplate(
        template,
        appLocation,
        projectName) map (result => result map (_ => appLocation))

    // Ensure feedback happens after clone-ing is done.
    for (result <- installed) {
      if (result.isSuccess)
        self ! Respond(Status("Template is cloned, compiling project definition..."))
      else
        log.warning("Failed to clone template: " + result)
    }
    loadApplicationAndSendResponse("CreateNewApplication", installed)
  }

  // Goes off and tries to open an application, responding with
  // whether or not we were successful to this actor.
  def openExistingApplication(location: String): Unit = {
    log.debug(s"Looking for existing application at: $location")
    // TODO - Ensure timeout is ok...
    val file = Validating(new File(location)).validate(
      Validation.fileExists,
      Validation.isDirectory)
    if (file.isSuccess)
      self ! Respond(Status("Compiling project definition..."))
    else
      log.warning(s"Failed to locate directory $location: " + file) // error response is generated in loadApplicationAndSendResponse
    import scala.concurrent.promise
    val filePromise = promise[ProcessResult[File]]
    filePromise.success(file)
    loadApplicationAndSendResponse("OpenExistingApplication", filePromise.future)
  }

  // helper method that given a validated file, will try to load
  // the application id and return an appropriate response.
  private def loadApplicationAndSendResponse(request: String, file: Future[ProcessResult[File]]) = {
    import context.dispatcher
    val id = file flatMapNested { file =>
      AppManager.loadAppIdFromLocation(file,
        Some({
          json => self ! Respond(json)
        }))
    }
    val response = id map {
      case ProcessSuccess(id) =>
        log.debug(s"HomeActor: Found application id: $id")
        RedirectToApplication(id)
      // TODO - Return with form and flash errors?
      case ProcessFailure(errors) =>
        log.warning(s"HomeActor: Failed to find application: ${errors map (_.msg) mkString "\n\t"}")
        BadRequest(request, errors map (_.msg))
    } recover {
      case NonFatal(e) => BadRequest(request, Seq(s"${e.getClass.getName}: ${e.getMessage}"))
    } map Respond.apply
    pipe(response) to self
  }
}
