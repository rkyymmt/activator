package controllers.api

import play.api.mvc.{Action, Controller}
import play.api.libs.json._
import play.api.Play
import java.io.File

object Local extends Controller {

  def getEnv = Action { request =>
    val localEnvJson = Json.toJson(
      Map(
        "desktopDir" -> Json.toJson((new File(System.getProperty("user.home"), "Desktop")).getAbsolutePath),
        "separator" -> Json.toJson(File.separator)
      )
    )

    Ok(localEnvJson)
  }
  
}
