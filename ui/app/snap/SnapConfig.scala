package snap

import play.api.libs.json._
import scala.concurrent._
import ExecutionContext.Implicits.global
import java.io._
import activator.properties.ActivatorProperties.ACTIVATOR_USER_HOME
import scala.concurrent.duration._
import sbt.IO

case class AppConfig(location: File, id: String, cachedName: Option[String] = None)

object AppConfig {
  import play.api.data.validation.ValidationError

  implicit object FileWrites extends Writes[File] {
    def writes(file: File) = JsString(file.getPath)
  }

  implicit val writes = Json.writes[AppConfig]

  implicit object FileReads extends Reads[File] {
    def reads(json: JsValue) = json match {
      case JsString(path) => JsSuccess(new File(path))
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.jsstring"))))
    }
  }

  implicit val reads = Json.reads[AppConfig]
}

case class RootConfig(applications: Seq[AppConfig])

object RootConfig {
  implicit val writes = Json.writes[RootConfig]
  implicit val reads = Json.reads[RootConfig]

  private def loadUser = ConfigFile(new File(ACTIVATOR_USER_HOME(), "config.json"))

  // volatile because we read it unsynchronized. we don't care
  // which one we get, just something sane. Also double-checked
  // locking below requires volatile.
  // this is an Option so we can make forceReload() defer reloading
  // by setting to None and then going back to Some "on demand"
  @volatile private var userFutureOption: Option[Future[ConfigFile]] = Some(loadUser)

  def forceReload(): Unit = {
    // we want to ensure we reload the file next time, but
    // avoid kicking off the reload now since we probably JUST
    // discovered the file was broken.
    userFutureOption = None
  }

  // get the current per-user configuration
  def user: RootConfig = try {
    // double-checked locking
    val userFuture = userFutureOption match {
      case None => synchronized {
        if (userFutureOption.isEmpty)
          userFutureOption = Some(loadUser)
        userFutureOption.get
      }
      case Some(f) => f
    }
    // we use the evil Await because 99% of the time we expect
    // the Future to be completed already.
    Await.result(userFuture.map(_.config), 8.seconds)
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
      val userFuture = userFutureOption.getOrElse(loadUser) flatMap { configFile =>
        ConfigFile.rewrite(configFile)(f)
      }
      userFutureOption = Some(userFuture)
      userFuture map { _ => () }
    }
  }
}

private[snap] class ConfigFile(val file: File, json: JsValue) {
  val config = json.as[RootConfig]
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
        new ConfigFile(file, Json.toJson(RootConfig(Seq.empty[AppConfig])))
    }
  }

  def rewrite(configFile: ConfigFile)(f: RootConfig => RootConfig): Future[ConfigFile] = {
    val newJson = Json.toJson(f(configFile.config))

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
