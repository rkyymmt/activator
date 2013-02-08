package controllers

import play.api.mvc.{ Action, Controller, WebSocket }
import java.io.File
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.typesafe.sbtchild.SbtChildProcessMaker
import play.api.libs.json.{ JsString, JsObject, JsArray, JsNumber, JsValue }
import snap.{ RootConfig, AppConfig, AppManager }
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
  blueprint: Option[String]) {

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
        case Some(name) => Redirect(routes.Application.app(name))
        // TODO we need to have an error message and "flash" it then
        // display it on home screen
        case _ => Redirect(routes.Application.forceHome)
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
      // Attempt to create the new location and return a Try, so we have
      // a chance of knowing what the error is.
      val location: Future[File] =
        Future {
          val model = newAppForm.bindFromRequest.get
          val location = new File(model.location)
          // TODO - Store template cache somehwere better...
          snap.cache.Actions.cloneTemplate(
            api.Templates.templateCache,
            model.blueprint,
            location)
          location
        }
      // Now look up the app name and register this location
      // with recently loaded apps.
      val id = location flatMap AppManager.loadAppIdFromLocation
      id map {
        case Some(id) => Redirect(routes.Application.app(id))
        case _ =>
          // TODO - Fill the form with old values...
          BadRequest(views.html.home(homeModel, newAppForm))
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
    val file = try {
      val f = new File(form.location)
      if (!f.exists)
        throw new RuntimeException("does not exist: " + form.location)
      Right(f)
    } catch {
      case e: Exception =>
        Left(e)
    }
    file.fold({ e =>
      BadRequest(e.getMessage())
    }, { file =>
      // TODO loadAppIdFromLocation should return an Either with an error string
      val id = AppManager.loadAppIdFromLocation(file)
      Async(id map {
        case Some(id) => Ok(JsObject(Seq("id" -> JsString(id))))
        case _ =>
          BadRequest("failed to load app at " + file.getAbsolutePath)
      })
    })
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
      (app.actor ? snap.CreateWebSocket).mapTo[(Iteratee[JsValue, _], Enumerator[JsValue])].map { streams =>
        Logger.info("WebSocket streams created")
        streams
      }
    }

    streamsFuture onFailure {
      case e: Throwable =>
        Logger.warn("WebSocket failed to open: " + e.getMessage, e)
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
      app.config.location.getAbsolutePath,
      Seq("plugins/code/code", "plugins/run/run"),
      app.config.cachedName getOrElse app.config.id,
      // TODO - something less lame than exception here...
      app.blueprintID)

  /** The current working directory of the app. */
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)
}
