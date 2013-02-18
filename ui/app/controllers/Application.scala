package controllers

import play.api.mvc.{ Action, Controller, WebSocket }
import java.io.File
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.typesafe.sbtchild.SbtChildProcessMaker
import play.api.libs.json.{ JsString, JsObject, JsArray, JsNumber, JsValue }
import snap.{ RootConfig, AppConfig, AppManager, ProcessResult, Platform }
import snap.cache.TemplateMetadata
import snap.properties.SnapProperties
import scala.util.control.NonFatal
import scala.util.Try
import play.Logger
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import akka.pattern._

case class ApplicationModel(
  id: String,
  location: String,
  plugins: Seq[String],
  name: String,
  blueprint: Option[String],
  recentApps: Seq[AppConfig]) {

  def jsLocation = location.replaceAll("'", "\\'")
}

case class HomeModel(
  userHome: String,
  templates: Seq[TemplateMetadata],
  recentApps: Seq[AppConfig])

// Data we get from the new application form.
case class NewAppForm(
  name: String,
  location: String,
  blueprint: String)

case class FromLocationForm(location: String)

// Here is where we detect if we're running at a given project...
object Application extends Controller {

  /**
   * Our index page.  Either we load an app from the CWD, or we direct
   * to the homepage to create a new app.
   */
  def index = Action {
    Async {
      AppManager.loadAppIdFromLocation(cwd) map {
        case snap.ProcessSuccess(name) => Redirect(routes.Application.app(name))
        case snap.ProcessFailure(errors) =>
          // TODO FLASH THE ERROR, BABY
          Redirect(routes.Application.forceHome)
      }
    }
  }

  import play.api.data._
  import play.api.data.Forms._
  /** The new application form on the home page. */
  val newAppForm = Form(
    mapping(
      "name" -> text,
      "location" -> text,
      "blueprint" -> text)(NewAppForm.apply)(NewAppForm.unapply))

  /**
   * Creates a new application and loads it, or redirects to
   * the home page.
   */
  def newApplication = Action { implicit request =>
    Async {
      val form = newAppForm.bindFromRequest
      // Attempt to create the new location and return a Try, so we have
      // a chance of knowing what the error is.
      val location: Future[ProcessResult[File]] =
        Future {
          val model = form.get
          val location = new File(model.location)
          // TODO - Store template cache somehwere better...
          snap.cache.Actions.cloneTemplate(
            api.Templates.templateCache,
            model.blueprint,
            location) map (_ => location)
        }
      // Now look up the app name and register this location
      // with recently loaded apps.
      import concurrent.ExecutionContext.Implicits.global
      val id = location flatMapNested AppManager.loadAppIdFromLocation
      id map {
        case snap.ProcessSuccess(id) => Redirect(routes.Application.app(id))
        case snap.ProcessFailure(errrors) =>
          // TODO - flash the errors we now have...
          BadRequest(views.html.home(homeModel, form))
      }
    }
  }
  /** Reloads the model for the home page. */
  private def homeModel = HomeModel(
    userHome = SnapProperties.GLOBAL_USER_HOME,
    templates = api.Templates.templateCache.metadata.toSeq,
    recentApps = RootConfig.user.applications)

  /** Loads the homepage, with a blank new-app form. */
  def forceHome = Action { request =>
    // TODO - make sure template cache lives in one and only one place!
    Ok(views.html.home(homeModel, newAppForm))
  }
  /** Loads an application model and pushes to the view by id. */
  def app(id: String) = Action { implicit request =>
    Async {
      // TODO - Different results of attempting to load the application....
      AppManager.loadApp(id) map { theApp =>
        Ok(views.html.application(getApplicationModel(theApp)))
      } recover {
        case e: Exception =>
          // TODO we need to have an error message and "flash" it then
          // display it on home screen
          Logger.error("Failed to load app id " + id + ": " + e.getMessage(), e)
          Redirect(routes.Application.forceHome)
      }
    }
  }

  val fromLocationForm = Form(
    mapping(
      "location" -> text)(FromLocationForm.apply)(FromLocationForm.unapply))

  /**
   * Registers a location as an application, returning JSON with the app ID.
   * Basically this is "import existing directory"
   */
  def appFromLocation() = Action { implicit request =>
    val form = fromLocationForm.bindFromRequest.get

    val file = snap.Validating(new File(form.location)).validate(
      snap.Validation.fileExists,
      snap.Validation.isDirectory)
    import concurrent.ExecutionContext.Implicits.global
    val id = file flatMapNested AppManager.loadAppIdFromLocation

    Async {
      id map {
        case snap.ProcessSuccess(id) => Ok(JsObject(Seq("id" -> JsString(id))))
        // TODO - Return with form and flash errors?
        case snap.ProcessFailure(errors) => BadRequest(errors map (_.msg) mkString "\n")
      }
    }
  }

  /**
   * Connects from an application page to the "stateful" actor/server we use
   * per-application for information.
   */
  def connectApp(id: String) = WebSocket.async[JsValue] { request =>
    Logger.info("Connect request for app id: " + id)
    val streamsFuture = AppManager.loadApp(id) flatMap { app =>
      // this is just easier to debug than a timeout; it isn't reliable
      if (app.actor.isTerminated) throw new RuntimeException("App is dead")

      import snap.WebSocketActor.timeout
      (app.actor ? snap.CreateWebSocket).map {
        case snap.WebSocketAlreadyUsed =>
          throw new RuntimeException("can only open apps in one tab at a time")
        case whatever => whatever
      }.mapTo[(Iteratee[JsValue, _], Enumerator[JsValue])].map { streams =>
        Logger.info("WebSocket streams created")
        streams
      }
    }

    streamsFuture onFailure {
      case e: Throwable =>
        Logger.warn("WebSocket failed to open: " + e.getMessage)
    }

    streamsFuture
  }

  /** List all the applications in our history as JSON. */
  def getHistory = Action { request =>
    Ok(JsArray(RootConfig.user.applications.map(_.toJson)))
  }

  /**
   * Returns the application model (for rendering the page) based on
   * the current snap App.
   */
  def getApplicationModel(app: snap.App) =
    ApplicationModel(
      app.config.id,
      Platform.getClientFriendlyFilename(app.config.location),
      Seq("plugins/code/code", "plugins/run/run", "plugins/test/test"),
      app.config.cachedName getOrElse app.config.id,
      // TODO - something less lame than exception here...
      app.blueprintID,
      RootConfig.user.applications)

  /** The current working directory of the app. */
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)
}
