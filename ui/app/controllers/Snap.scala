package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsString, JsObject, JsArray, JsNumber}
import play.api.Play
import sys.process.Process
import java.io.File

object Snap extends Controller {

  def home = Action { request =>
    Ok(views.html.home())
  }

  def app(id: String) = Action { request =>
    Ok(views.html.app())
  }
  
}
