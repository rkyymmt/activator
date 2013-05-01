package controllers.api

import play.api._
import play.api.mvc._
import play.api.libs.json._
import snap.cache.TemplateMetadata
import scala.util.control.NonFatal
import scala.concurrent.duration._

object Templates extends Controller {
  // This will load our template cache and ensure all templates are available for the demo.
  // We should think of an alternative means of loading this in the future.
  // TODO - We should load timeout from configuration.
  implicit val timeout = akka.util.Timeout(Duration(6, SECONDS))
  val templateCache = snap.cache.DefaultTemplateCache(snap.Akka.system)

  // Here's the JSON rendering of template metadata.
  implicit object Protocol extends Format[TemplateMetadata] {
    def writes(o: TemplateMetadata): JsValue =
      JsObject(
        List("id" -> JsString(o.id),
          "name" -> JsString(o.name),
          "title" -> JsString(o.title),
          "timestamp" -> JsNumber(o.timeStamp),
          "description" -> JsString(o.description),
          "tags" -> JsArray(o.tags map JsString.apply)))
    //We don't need reads, really
    def reads(json: JsValue): JsResult[TemplateMetadata] =
      JsError("Reading TemplateMetadata not supported!")
  }

  def list = Action { request =>
    Async {
      import concurrent.ExecutionContext.Implicits._
      templateCache.metadata map { m => Ok(Json toJson m) }
    }
  }

  def tutorial(id: String, location: String) = Action { request =>
    Async {
      import concurrent.ExecutionContext.Implicits._
      templateCache tutorial id map { tutorialOpt =>
        // TODO - Use a Validation  applicative functor so this isn't so ugly. 
        val result =
          for {
            tutorial <- tutorialOpt
            file <- tutorial.files get location
          } yield file
        result match {
          case Some(file) => Ok sendFile file
          case _ => NotFound
        }
      }
    }
  }

  def cloneTemplate = Action(parse.json) { request =>

    val location = new java.io.File((request.body \ "location").as[String])
    val templateid = (request.body \ "template").as[String]
    val name = (request.body \ "name").asOpt[String]
    Async {
      import scala.concurrent.ExecutionContext.Implicits._
      val result = snap.cache.Actions.cloneTemplate(templateCache, templateid, location, name)
      result.map(x => Ok(request.body)).recover {
        case e => NotAcceptable(e.getMessage)
      }
    }
  }
}