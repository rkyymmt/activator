package controllers.api

import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsString, JsObject, JsArray, JsNumber}
import play.api.Play
import sys.process.Process
import java.io.File

object App extends Controller {

  def getDetails(location: String) = Action { request =>

    Ok(
      JsObject(
        Seq(
          "name" -> JsString("My First Amazing App")
        )
      )
    )
  }
  
  def getPlugins(location: String) = Action { request =>

    Ok(
      JsArray(
        Seq(
          JsObject(
            Seq(
              "id" -> JsString("code"),
              "name" -> JsString("Code"),
              "position" -> JsNumber(0)
            )
          ),
          JsObject(
            Seq(
              "id" -> JsString("run"),
              "name" -> JsString("Run"),
              "position" -> JsNumber(1)
            )
          ),
          JsObject(
            Seq(
              "id" -> JsString("test"),
              "name" -> JsString("Test"),
              "position" -> JsNumber(2)
            )
          ),
          JsObject(
            Seq(
              "id" -> JsString("console"),
              "name" -> JsString("Console"),
              "position" -> JsNumber(3)
            )
          )
        )
      )
    )
  }

  def openLocation(location: String) = Action { request =>
    if ((new File(location)).exists()) {

      val command = System.getProperty("os.name") match {
        case "Linux" => "/usr/bin/xdg-open file://" + location
      }
  
      Runtime.getRuntime.exec(command)
  
      Ok("")
    }
    else {
      NotAcceptable("The location was not found: " + location)
    }
  }

  def startApp(location: String) = Action { request =>

    Process(Seq(Play.current.path.getParentFile + "/snap", "~run"), new File(location)).run()

    Ok("")

  }
  
}
