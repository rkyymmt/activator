package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play

import scala.sys.process.Process

import java.io.File
import org.apache.commons.io.FileUtils

object Application extends Controller {

  def cloneTemplate = Action(parse.json) { request =>
    
    val name = (request.body \ "name").as[String]
    val location = new File((request.body \ "location").as[String])
    val template = (request.body \ "template").as[String]
    
    val templateDir = new File(Play.current.path.getParentFile, "templates/" + template)
    
    if (!templateDir.exists()) {
      NotAcceptable("Template " + templateDir + " not found") // todo: to json
    }
    else if (location.exists()) {
      NotAcceptable("Location " + location.getAbsolutePath + " exists") // todo: to json
    }
    else {
      FileUtils.copyDirectory(templateDir, location)
      Ok(request.body)
    }
  }
  
  def openLocation = Action(parse.json) { request =>

    val location = (request.body \ "location").as[String]
    
    val command = System.getProperty("os.name") match {
      case "Linux" => "/usr/bin/xdg-open file://" + location
    }
    
    Runtime.getRuntime.exec(command)

    Ok(request.body)
  }
  
  def startApp = Action(parse.json) { request =>

    val location = new File((request.body \ "location").as[String])

    //val app = PlayProject
    
    Process(Seq(Play.current.path.getParentFile + "/snap", "~run"), location).run()

    Ok(request.body)
    
  }

}