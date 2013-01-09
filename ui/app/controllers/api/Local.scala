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
  
  // Here's the JSON rendering of template metadata.
  implicit object FileProtocol extends Format[File] {
    def writes(o: File): JsValue =
      JsObject(
            List("name" -> JsString(o.getName),
                "location" -> JsString(o.getCanonicalPath),
                "isDirectory" -> JsBoolean(o.isDirectory)
            )
        )
    //We don't need reads, really
    def reads(json: JsValue): JsResult[File] =
      JsError("Reading TemplateMetadata not supported!")
  }
  case class InterestingFile(file: File)
  implicit object IFileProtocol extends Format[InterestingFile] {
    def writes(o: InterestingFile): JsValue =
      if(o.file.isDirectory) JsObject(List("children" -> Json.toJson(o.file.listFiles())))
      else JsObject(List("contents" -> JsString(""))) // TODO - Load the file.
    //We don't need reads, really
    def reads(json: JsValue): JsResult[InterestingFile] =
      JsError("Reading Files not supported!")
  }
  

  def browse(location: String) = Action { request =>
    val loc = new java.io.File(location)
    if(!loc.exists) NotAcceptable(s"${location} is not a file!")
    else Ok(Json toJson InterestingFile(loc))
  }
}
