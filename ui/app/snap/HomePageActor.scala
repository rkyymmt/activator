package snap

import akka.actor._
import play.api.libs.json._
import play.api.libs.iteratee.Concurrent
import java.io.File
import akka.pattern.pipe
import scala.util.control.NonFatal

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
}
class HomePageActor extends WebSocketActor[JsValue] with ActorLogging {
  import HomePageActor._
  override def onMessage(json: JsValue): Unit = json match {
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
    val installed: ProcessResult[File] =
      //TODO - Store template cache somehwere better...
      snap.cache.Actions.cloneTemplate(
        controllers.api.Templates.templateCache,
        template,
        appLocation,
        projectName) map (_ => appLocation)
    if (installed.isSuccess)
      self ! Respond(Status("Template is cloned, compiling project definition..."))
    else
      log.warning("Failed to clone template: " + installed) // error response is generated in loadApplicationAndSendResponse
    loadApplicationAndSendResponse("CreateNewApplication", installed)
  }

  // Goes off and tries to open an application, responding with
  // whether or not we were successful to this actor.
  def openExistingApplication(location: String): Unit = {
    log.debug(s"Looking for existing application at: $location")
    // TODO - Ensure timeout is ok...
    val file = snap.Validating(new File(location)).validate(
      snap.Validation.fileExists,
      snap.Validation.isDirectory)
    if (file.isSuccess)
      self ! Respond(Status("Compiling project definition..."))
    else
      log.warning(s"Failed to locate directory $location: " + file) // error response is generated in loadApplicationAndSendResponse
    loadApplicationAndSendResponse("OpenExistingApplication", file)
  }

  // helper method that given a validated file, will try to load
  // the application id and return an appropriate response.
  private def loadApplicationAndSendResponse(request: String, file: ProcessResult[File]) = {
    import context.dispatcher
    val id = file flatMapNested { file =>
      AppManager.loadAppIdFromLocation(file,
        Some({
          json => self ! Respond(json)
        }))
    }
    val response = id map {
      case snap.ProcessSuccess(id) =>
        log.debug(s"HomeActor: Found application id: $id")
        RedirectToApplication(id)
      // TODO - Return with form and flash errors?
      case snap.ProcessFailure(errors) =>
        log.warning(s"HomeActor: Failed to find application: ${errors map (_.msg) mkString "\n\t"}")
        BadRequest(request, errors map (_.msg))
    } recover {
      case NonFatal(e) => BadRequest(request, Seq(s"${e.getClass.getName}: ${e.getMessage}"))
    } map Respond.apply
    pipe(response) to self
  }
}
