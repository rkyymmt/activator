package controllers

import play.api.mvc.{ Action, Controller }
import java.io.File
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.typesafe.sbtchild.SbtChildProcessMaker
import play.api.libs.json.{ JsString, JsObject, JsArray, JsNumber }
import snap.{ RootConfig, AppConfig }

case class ApplicationModel(
  location: String,
  plugins: Seq[String]) {

  def jsLocation = location.replaceAll("'", "\\'")
}

// Here is where we detect if we're running at a given project...
object Application extends Controller {

  // this is supposed to be set by the main() launching the UI
  @volatile var sbtChildProcessMaker: SbtChildProcessMaker = _
  // TODO(jsuereth) - Initialize in dev mode since Prod mode will override in UIMain.

  def index = Action {
    Async {
      loadAppName(cwd) map {
        // TODO - Wait for cached name....
        case Some(name) => Redirect(routes.Application.app(name))
        case _ => Redirect(routes.Application.forceHome)
      }
    }
  }

  def forceHome = Action { request =>
    Ok(views.html.home())
  }

  def app(id: String) = Action { request =>
    Async {
      // TODO - Different results of attempting to load the application....
      loadApp(id) map {
        case Some(app) => Ok(views.html.application(getApplicationModel(app)))
        case _ => Redirect(routes.Application.forceHome)
      }
    }
  }

  // list all apps in the config
  def getHistory = Action { request =>
    Ok(JsArray(RootConfig.user.projects.map(_.toJson)))
  }

  // TODO - actually load from file or something which plugins we use.
  def getApplicationModel(app: snap.App) =
    ApplicationModel(app.config.location.getAbsolutePath,
      Seq("plugins/code/code", "plugins/play/play"))

  // TODO - Better detection, in library most likely.
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
        snap.ProjectConfig(cwd, id, None),
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
