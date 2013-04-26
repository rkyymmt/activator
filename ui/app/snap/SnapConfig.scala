package snap

// for Play's JsValue.as[] implicits
import language.higherKinds
// for the "5 seconds" duration syntax
import language.postfixOps

import play.api.libs.json._
import scala.concurrent._
import ExecutionContext.Implicits.global
import java.io._
import activator.properties.ActivatorProperties.ACTIVATOR_USER_HOME
import scala.concurrent.duration._

case class AppConfig(location: File, id: String, cachedName: Option[String] = None) {
  def toJson: JsObject = {
    val locationField = "location" -> JsString(location.getPath)
    val idField = "id" -> JsString(id)
    val nameFieldOption = cachedName.map({ name => "name" -> JsString(name) })
    JsObject(Seq(idField, locationField) ++ nameFieldOption.toSeq)
  }
}

object AppConfig {
  def apply(json: JsObject): AppConfig = {
    val location = (json \ "location").as[String]
    val id = (json \ "id").as[String]
    val nameOption = (json \ "name").asOpt[String]
    AppConfig(new File(location), id, nameOption)
  }
}

case class RootConfig(applications: Seq[AppConfig]) {
  def toJson: JsObject = {
    JsObject(Seq(
      "applications" -> JsArray(applications.map(_.toJson))))
  }
}

object RootConfig {
  def apply(json: JsObject): RootConfig = {
    val applications = json \ ("applications") match {
      case JsArray(list) =>
        list.map({
          case o: JsObject => AppConfig(o)
          case whatever => throw new Exception("invalid JSON for project: " + whatever)
        })
      case whatever =>
        Nil
    }
    RootConfig(applications)
  }

  private def loadUser = ConfigFile(new File(ACTIVATOR_USER_HOME(), "config.json"))

  // volatile because we read it unsynchronized. we don't care
  // which one we get, just something sane.
  @volatile private var userFuture = loadUser

  def forceReload(): Unit = {
    userFuture = loadUser
  }

  // get the current per-user configuration
  def user: RootConfig = try {
    // we use the evil Await because 99% of the time we expect
    // the Future to be completed already.
    Await.result(userFuture.map(_.config), 8 seconds)
  } catch {
    case e: Exception =>
      // retry next time
      forceReload()
      // but go ahead and throw this time
      throw e
  }

  // modify the per-user configuration
  def rewriteUser(f: RootConfig => RootConfig): Future[Unit] = {
    // the "synchronized" is intended to ensure that all "f"
    // transformations in fact take place, though in undefined
    // order. Otherwise we could use the same future twice as
    // the "old" and generate two "new" one of which would be
    // discarded.
    synchronized {
      // note that the actual file-rewriting is NOT synchronized,
      // it is async. We're just synchronizing storing the Future
      // in our var so that no Future is "skipped"
      userFuture = userFuture flatMap { configFile =>
        ConfigFile.rewrite(configFile)(f)
      }
      userFuture map { _ => () }
    }
  }
}

private[snap] class ConfigFile(val file: File, json: JsObject) {
  val config = RootConfig(json)

}

private[snap] object ConfigFile {
  def apply(file: File): Future[ConfigFile] = {
    future {
      val input = new FileInputStream(file)
      val s = try {
        val out = new ByteArrayOutputStream()
        copy(input, out)
        new String(out.toString("UTF-8"))
      } finally {
        input.close()
      }
      val obj = Json.parse(s) match {
        case x: JsObject => x
        case whatever => throw new Exception("config file contains non-JSON-object")
      }
      new ConfigFile(file, obj)
    } recover {
      case e: FileNotFoundException =>
        new ConfigFile(file, JsObject(Seq.empty))
    }
  }

  def rewrite(configFile: ConfigFile)(f: RootConfig => RootConfig): Future[ConfigFile] = {
    val newJson = f(configFile.config).toJson

    future {
      // we parse the json we create back before doing any IO, as a sanity check
      val newConfig = new ConfigFile(configFile.file, newJson)

      val tmpFile = new File(newConfig.file.getCanonicalPath + ".tmp")
      ignoringIOException { IO.createDirectory(tmpFile.getParentFile) }
      ignoringIOException { IO.delete(tmpFile) }
      val bytesToWrite = newJson.toString.getBytes("UTF-8")
      val out = new FileOutputStream(tmpFile)
      try {
        val in = new ByteArrayInputStream(bytesToWrite)
        copy(in, out)
      } finally {
        out.close()
      }
      // kind of a silly paranoia check
      if (tmpFile.length() != bytesToWrite.length)
        throw new IOException("File does not have expected size: " + tmpFile.getCanonicalPath() + ": " + bytesToWrite.length)
      // then copy over
      IO.move(tmpFile, newConfig.file)

      newConfig
    }
  }

  private def ignoringIOException[T](block: => T): Unit = {
    try {
      block
    } catch {
      case e: IOException => ()
    }
  }

  private val MAX_BUF = 1024 * 1024
  private val MIN_BUF = 1024

  private def copy(in: InputStream, out: OutputStream): Long = {
    val buf = new Array[Byte](Math.min(MAX_BUF, Math.max(MIN_BUF, in.available())))
    var bytesWritten = 0
    var bytesRead = 0
    bytesRead = in.read(buf)
    while (bytesRead != -1) {
      out.write(buf, 0, bytesRead)
      bytesWritten += bytesRead
      bytesRead = in.read(buf)
    }
    bytesWritten
  }
}
