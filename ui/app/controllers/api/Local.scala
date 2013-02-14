package controllers.api

import play.api.mvc.{ Action, Controller }
import play.api.libs.json._
import play.api.Play
import java.io.File
import snap.Platform
import play.api.data._
import play.api.data.Forms._

object Local extends Controller {

  def getEnv = Action { request =>
    val localEnvJson = Json.toJson(
      Map(
        "desktopDir" ->
          Json.toJson(Platform.getClientFriendlyFilename(new File(System.getProperty("user.home"), "Desktop"))),
        "separator" -> Json.toJson(File.separator)))

    Ok(localEnvJson)
  }

  def getExtension(name: String): String =
    (for {
      n <- Option(name)
      idx = n lastIndexOf '.'
      if idx != -1
      sub = n substring idx
    } yield (sub drop 1)) getOrElse ""

  // TODO - Make this configurable!
  def getFileType(name: String): String = getExtension(name) match {
    case "jpg" => "image"
    case "png" => "image"
    case "gif" => "image"
    case "html" => "code"
    case "java" => "code"
    case "scala" => "code"
    case "sbt" => "code"
    case "js" => "code"
    case "css" => "code"
    case "less" => "code"
    case "text" => "code"
    case "md" => "code"
    case "rst" => "code"
    case "properties" => "code"
    case "conf" => "code"
    // TODO - New "tail" viewer for logs?
    case "log" => "code"

    // If we can't find any specific handler based on extension, certain files still need
    // to be handled by name....
    case _ => name match {
      case "routes" => "code"
      case _ => "binary" // Assume binary ok? 
    }
  }

  // Here's the JSON rendering of template metadata.
  implicit object FileProtocol extends Format[File] {
    def writes(o: File): JsValue =
      JsObject(
        List("name" -> JsString(o.getName),
          "location" -> JsString(Platform.getClientFriendlyFilename(o)),
          "isDirectory" -> JsBoolean(o.isDirectory)) ++ (if (o.isDirectory) Nil else List("type" -> JsString(getFileType(o.getName)))))
    //We don't need reads, really
    def reads(json: JsValue): JsResult[File] =
      JsError("Reading TemplateMetadata not supported!")
  }
  case class InterestingFile(file: File)
  implicit object IFileProtocol extends Format[InterestingFile] {
    def writes(o: InterestingFile): JsValue =
      if (o.file.isDirectory) JsObject(List(
        "type" -> JsString("directory"),
        // TODO - Gitignore/file filters here.
        "children" -> Json.toJson(o.file.listFiles().filterNot(_.getName startsWith "."))))
      else FileProtocol.writes(o.file)
    //We don't need reads, really
    def reads(json: JsValue): JsResult[InterestingFile] =
      JsError("Reading Files not supported!")
  }

  def browse(location: String) = Action { request =>
    val loc = Platform.fromClientFriendlyFilename(location)
    if (!loc.exists) NotAcceptable(s"${location} is not a file!")
    else Ok(Json toJson InterestingFile(loc))
  }

  def show(location: String) = Action { request =>
    val loc = Platform.fromClientFriendlyFilename(location)
    if (!loc.exists) NotAcceptable(s"${location} is not a file!")
    else (Ok sendFile loc)
  }

  val saveFileForm = Form(tuple("location" -> text, "content" -> text))
  def save = Action { implicit request =>
    // TODO - use Validation here.
    val (location, content) = saveFileForm.bindFromRequest.get
    val loc = Platform.fromClientFriendlyFilename(location)
    // We should probably just save any file...
    import snap.cache.IO
    if (!loc.exists) NotAcceptable(s"${location} is not a file!")
    else {
      snap.cache.IO.write(loc, content)
      Ok(content)
    }
  }
}
