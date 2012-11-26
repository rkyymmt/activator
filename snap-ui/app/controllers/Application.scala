package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play

import java.io.File

object Application extends Controller {

  def cloneTemplate = Action(parse.json) { request =>
    
    val name = (request.body \ "name").as[String]
    val location = (request.body \ "location").as[String]
    val template = (request.body \ "template").as[String]
    
    //val templateDir = Play.current.path.parent
    
    
    Ok(request.body)
  }

}