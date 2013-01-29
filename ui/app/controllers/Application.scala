package controllers

import play.api.mvc.{ Action, Controller }
import java.io.File
import snap.cache.TemplateMetadata
import snap.properties.SnapProperties

case class ApplicationModel(
  location: String,
  plugins: Seq[String]) {

  def jsLocation = location.replaceAll("'", "\\'")
}

case class HomeModel(
  userHome: String,
  templates: Seq[TemplateMetadata])

// Here is where we detect if we're running at a given project...
object Application extends Controller {
  def index = Action {
    if (isOnProject(cwd)) Redirect(routes.Application.app)
    else Redirect(routes.Application.forceHome)
  }

  def forceHome = Action { request =>
    // TODO - make sure template cache lives in one and only one place!
    Ok(views.html.home(HomeModel(
      userHome = SnapProperties.GLOBAL_USER_HOME,
      templates = api.Templates.templateCache.metadata.toSeq)))
  }

  def app = Action { request =>
    val location = request.getQueryString("location") map (new File(_)) getOrElse cwd
    if (isOnProject(location)) Ok(views.html.application(getApplicationModel(location)))
    else Redirect(routes.Application.index)
  }

  // TODO - actually load from file or something which plugins we use.
  def getApplicationModel(projectDir: File) =
    ApplicationModel(projectDir.getAbsolutePath,
      Seq("plugins/code/code", "plugins/play/play"))

  // TODO - Better detection, in library most likely.
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)
  def isOnProject(dir: File) = (new java.io.File(dir, "project/build.properties")).exists
}
