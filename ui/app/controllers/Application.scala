package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play

import scala.sys.process.Process

import java.io.File
import org.apache.commons.io.FileUtils

object Application extends Controller {
  // This will load our template cache and ensure all templates are available for the demo.
  // We should think of an alternative means of loading this in the future.
  val templateCache = snap.cache.TemplateCache()

  def cloneTemplate = Action(parse.json) { request =>
    
    val name = (request.body \ "name").as[String]
    val location = new File((request.body \ "location").as[String])
    val templateid = (request.body \ "template").as[String]
    
    val template = templateCache.template(templateid)
    if (!template.isDefined) {
      NotAcceptable("Template " + templateid + " not found") // todo: to json
    }
    else if (location.exists()) {
      NotAcceptable("Location " + location.getAbsolutePath + " exists") // todo: to json
    }
    else {
      //  Copy all files from the template dir.
      // TODO - Use SBT IO when it's available.
      for {
        t <- template
        (file, path) <- t.files
        to = new File(location, path)
      } if(file.isDirectory) snap.cache.IO.createDirectory(to)
        else snap.cache.IO.copyFile(file, to)
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
