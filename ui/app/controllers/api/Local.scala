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
  def getFileType(file: File): String = getExtension(file.getName) match {
    case "html" => "code"
    case "scala" => "code"
    case "java" => "code"
    case "jpg" => "image"
    case "js" => "code"
    case "gif" => "image"
    case "png" => "image"
    case "sbt" => "code"
    case "conf" => "code"
    case "css" => "code"
    case "less" => "code"
    case "text" => "code"
    case "md" => "code"
    case "rst" => "code"
    case "properties" => "code"
    case "bat" => "code"
    // TODO - New "tail" viewer for logs?
    case "log" => "code"

    // If we can't find any specific handler based on extension, certain files still need
    // to be handled by name....
    case _ => file.getName match {
      case "routes" => "code"
      case _ => detectTypeFromMime(file)
    }
  }

  def detectTypeFromMime(f: File): String = {
    val mime = getMimeType(f)
    System.err.println(s"Checking mime type for $f, found: $mime");
    if (mime startsWith "text") "code"
    else if (mime startsWith "image") "image"
    else if (mime contains "x-shellscript") "code" // TODO - highlighting is still borked for this guy
    else "binary"
  }

  // Magically discover the mime type!
  def getMimeType(file: File): String = {
    import eu.medsea.mimeutil._
    MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
    val mimeTypes = MimeUtil getMimeTypes file
    MimeUtil.getFirstMimeType(mimeTypes.toString).toString
  }

  // Here's the JSON rendering of template metadata.
  implicit object FileProtocol extends Format[File] {
    def writes(o: File): JsValue =
      JsObject(
        List("name" -> JsString(o.getName),
          "location" -> JsString(Platform.getClientFriendlyFilename(o)),
          "humanLocation" -> JsString(o.getAbsolutePath),
          "isDirectory" -> JsBoolean(o.isDirectory)) ++
          (if (o.isDirectory) Nil
          else List(
            "type" -> JsString(getFileType(o)),
            "size" -> JsNumber(o.length))))
    //We don't need reads, really
    def reads(json: JsValue): JsResult[File] =
      JsError("Reading TemplateMetadata not supported!")
  }
  case class InterestingFile(file: File)
  implicit object IFileProtocol extends Format[InterestingFile] {
    def writes(o: InterestingFile): JsValue =
      if (o.file.isDirectory) JsObject(List(
        "name" -> JsString(o.file.getName),
        "location" -> JsString(Platform.getClientFriendlyFilename(o.file)),
        "humanLocation" -> JsString(o.file.getAbsolutePath),
        "isDirectory" -> JsBoolean(true),
        "type" -> JsString("directory"),
        // TODO - Only if parent file exists!
        "parent" -> Json.toJson(o.file.getParentFile),
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
    IO.withTemporaryFile("builder", "save-file") { file =>
      IO.write(file, content)
      IO.move(file, loc)
    }
    Ok(content)
  }
}
