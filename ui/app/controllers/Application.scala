package controllers

import play.api.mvc.{ Action, Controller }
import java.io.File
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

case class ApplicationModel(
  location: String,
  plugins: Seq[String]) {

  def jsLocation = location.replaceAll("'", "\\'")
}

// Here is where we detect if we're running at a given project...
object Application extends Controller {
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

  def app(name: String) = Action { request =>
    Async {
      // TODO - Different results of attempting to load the application....
      loadApp(name) map {
        case Some(app) => Ok(views.html.application(getApplicationModel(app)))
        case _ => Redirect(routes.Application.forceHome)
      }
    }
  }

  // TODO - actually load from file or something which plugins we use.
  def getApplicationModel(app: snap.App) =
    ApplicationModel(app.config.location.getAbsolutePath,
      Seq("plugins/code/code", "plugins/play/play"))

  // TODO - Better detection, in library most likely.
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)

  // Loads an application based on its name.
  // TODO - Don't have this stubbed out!
  private def loadApp(name: String): Future[Option[snap.App]] =
    Future {
      // TODO ->
      Some(new snap.App(
        snap.ProjectConfig(
          cwd,
          Some(name)),
        snap.Akka.system,
        null))
    }
  // Loads the name of an app based on the CWD.  Note: This is how we
  // redirect when first starting the UI.   We need to detect if the location
  // is an sbt project, start SBT and pull out the name of the root project,
  // Then return that name (as well as storing it in the recent app cache...)
  private def loadAppName(location: File): Future[Option[String]] =
    Future {
      // TODO - Don't cheat here!
      if (isOnProject(location)) Some("my-app")
      else None
    }

  def isOnProject(dir: File) = (new java.io.File(dir, "project/build.properties")).exists
}
