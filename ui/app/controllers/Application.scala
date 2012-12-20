package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play

import scala.sys.process.Process

import java.io.File
import org.apache.commons.io.FileUtils

object Application extends Controller {
  
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
