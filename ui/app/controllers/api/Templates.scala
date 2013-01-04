package controllers.api

import play.api._
import play.api.mvc._
import play.api.libs.json._
import snap.cache.TemplateMetadata
import scala.util.control.NonFatal


object Templates extends Controller {
  // This will load our template cache and ensure all templates are available for the demo.
  // We should think of an alternative means of loading this in the future.
  val templateCache = snap.cache.TemplateCache()
  
  // Here's the JSON rendering of template metadata.
  implicit object Protocol extends Format[TemplateMetadata] {
    def writes(o: TemplateMetadata): JsValue =
      JsObject(
            List("id" -> JsString(o.id),
                "name" -> JsString(o.name),
                "version" -> JsString(o.version),
                "description" -> JsString(o.description),
                "tags" -> JsArray(o.tags map JsString.apply)
            )
        )
    //We don't need reads, really
    def reads(json: JsValue): JsResult[TemplateMetadata] =
      JsError("Reading TemplateMetadata not supported!")
  }
  
  
  
  def list =  Action { request =>
    Ok(Json toJson templateCache.metadata)
  }
  
  
  def cloneTemplate = Action(parse.json) { request =>
    
    val location = new java.io.File((request.body \ "location").as[String])
    val templateid = (request.body \ "template").as[String]
    
    try {
      snap.cache.Actions.cloneTemplate(templateCache, templateid, location)
      Ok(request.body)
    } catch {
      case NonFatal(e) => NotAcceptable(e.getMessage)
    }
  }
}