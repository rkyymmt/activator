package controllers

import play.api.mvc.{ Action, Controller }
import java.io.File
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.typesafe.sbtchild.SbtChildProcessMaker
import play.api.libs.json.{ JsString, JsObject, JsArray, JsNumber }
import snap.{ RootConfig, AppConfig }
import snap.cache.TemplateMetadata
import snap.properties.SnapProperties
import scala.util.control.NonFatal
import scala.util.Try

case class ApplicationModel(
  location: String,
  plugins: Seq[String]) {

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

// Here is where we detect if we're running at a given project...
object Application extends Controller {

  // this is supposed to be set by the main() launching the UI.
  // If not, we know we're running inside the build and we need
  // to use the default "Debug" version.
  @volatile var sbtChildProcessMaker: SbtChildProcessMaker = snap.DebugSbtChildProcessMaker

  /**
   * Our index page.  Either we load an app from the CWD, or we direct
   * to the homepage to create a new app.
   */
  def index = Action {
    Async {
      loadAppName(cwd) map {
        // TODO - Wait for cached name....
        case Some(name) => Redirect(routes.Application.app(name))
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
      val id = location flatMap loadAppName
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
  def app(id: String) = Action { request =>
    Async {
      // TODO - Different results of attempting to load the application....
      loadApp(id) map {
        case Some(app) => Ok(views.html.application(getApplicationModel(app)))
        case _ => Redirect(routes.Application.forceHome)
      }
    }
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
    ApplicationModel(app.config.location.getAbsolutePath,
      Seq("plugins/code/code", "plugins/play/play"))

  /** The current working directory of the app. */
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)

  // Loads an application based on its id.
  // This needs to look in the RootConfig for the App/Location
  // based on this ID.
  // If the app id does not exist ->
  //    Return error (None or useful error)
  // If it exists
  //    Return the app
  private def loadApp(id: String): Future[Option[snap.App]] =
    Future {
      // TODO ->
      Some(new snap.App(
        snap.AppConfig(cwd, id, None),
        snap.Akka.system,
        null))
    }
  // Loads the name of an app based on the CWD.  
  // If we don't have an ID in RootConfig for this location, then 
  // - we should load the app and determine a good id
  // - we should store the id/location in the RootConfig
  // - We should return the new ID or None if this location is not an App.
  private def loadAppName(location: File): Future[Option[String]] =
    Future {
      // TODO - Don't cheat here!
      if (isOnProject(location)) Some("my-app")
      else None
    }

  def isOnProject(dir: File) = (new java.io.File(dir, "project/build.properties")).exists
}
