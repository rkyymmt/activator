package controllers

import play.api.mvc.{Action, Controller}
import java.io.File

case class ApplicationModel(
    location: String,
    plugins: Seq[String])

// Here is where we detect if we're running at a given project...
object Application extends Controller {


  def index = Action { request =>
    if(isOnProject(cwd)) Redirect(routes.Application.app)
    else Ok(views.html.home())
  }

  def forceHome = Action { request =>
    Ok(views.html.home())
  }

  def app = Action { request =>
    val location = request.getQueryString("location") map (new File(_)) getOrElse cwd
    //Ok(views.html.app())
    if(isOnProject(location)) Ok(views.html.application(getApplicationModel(location)))
    else Redirect(routes.Application.index)
  }
  
  // TODO - actually load from file or something which plugins we use.
  def getApplicationModel(projectDir: File) =
    ApplicationModel(projectDir.getAbsolutePath,
        Seq("plugins/code/code", "plugins/demo/demo"))
  
  
  // TODO - Better detection, in library most likely.
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)
  def isOnProject(dir: File) = (new java.io.File(dir, "project/build.properties")).exists
}
