package snap

import scala.concurrent.Future
import java.io.File
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Promise
import akka.pattern._
import com.typesafe.sbtchild._
import play.Logger
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.actor._

object AppManager {

  // this is supposed to be set by the main() launching the UI.
  // If not, we know we're running inside the build and we need
  // to use the default "Debug" version.
  @volatile var sbtChildProcessMaker: SbtChildProcessMaker = snap.DebugSbtChildProcessMaker

  // TODO as a first cut, just cache one app. As long as only one tab
  // is open this should be right, but of course it will be pure
  // fail when two tabs are open... the real solution should be
  // to tie one snap.App to each websocket, which will be associated
  // with a tab, then we kill the App along with the socket.
  @volatile var singleAppCache: Option[(String, Future[snap.App])] = None

  // Loads an application based on its id.
  // This needs to look in the RootConfig for the App/Location
  // based on this ID.
  // If the app id does not exist ->
  //    Return error
  // If it exists
  //    Return the app
  def loadApp(id: String): Future[snap.App] = singleAppCache match {
    case Some((cachedId, f)) if id == cachedId => f
    case other => {
      synchronized {
        singleAppCache.foreach(_._2.map(_.close()))

        val appFuture: Future[snap.App] = RootConfig.user.applications.find(_.id == id) match {
          case Some(config) =>
            Promise.successful(new snap.App(config, snap.Akka.system, sbtChildProcessMaker)).future
          case whatever =>
            Promise.failed(new RuntimeException("No such app with id: '" + id + "'")).future
        }

        singleAppCache = Some((id, appFuture))
        appFuture
      }
    }
  }

  // Loads the ID of an app based on the CWD.
  // If we don't have an ID in RootConfig for this location, then
  // - we should load the app and determine a good id
  // - we should store the id/location in the RootConfig
  // - We should return the new ID or None if this location is not an App.
  def loadAppIdFromLocation(location: File): Future[Option[String]] = {
    val absolute = location.getAbsoluteFile()
    RootConfig.user.applications.find(_.location == absolute) match {
      case Some(app) => Promise.successful(Some(app.id)).future
      case None => {
        doInitialAppAnalysis(location) map {
          _ match {
            case Left(error) =>
              Logger.error("Failed to load app at: " + location.getAbsolutePath())
              None
            case Right(appConfig) =>
              Some(appConfig.id)
          }
        }
      }
    }
  }

  // choose id "name", "name-1", "name-2", etc.
  // should always be called inside rewriteUser to avoid
  // a race creating the same ID
  private def newIdFromName(root: RootConfig, name: String, suffix: Int = 0): String = {
    val candidate = name + (if (suffix > 0) "-" + suffix.toString else "")
    root.applications.find(_.id == candidate) match {
      case Some(app) => newIdFromName(root, name, suffix + 1)
      case None => candidate
    }
  }

  private def doInitialAppAnalysis(location: File): Future[Either[String, AppConfig]] = {
    if (location.isDirectory()) {
      val sbt = SbtChild(snap.Akka.system, location, sbtChildProcessMaker)
      // TODO we need to test what happens here if sbt fails to start up (i.e. bad build files)
      implicit val timeout = Timeout(60, TimeUnit.SECONDS)
      val result: Future[Either[String, AppConfig]] = (sbt ? protocol.NameRequest) map { reply =>
        reply match {
          case protocol.NameResponse(name, logs) => {
            Logger.info("sbt told us the name is: '" + name + "'")
            Right(name)
          }
          case protocol.ErrorResponse(error, logs) =>
            Logger.info("error getting name from sbt: " + error)
            Left(error)
        }
      } flatMap { errorOrName =>
        errorOrName match {
          case Right(name) => RootConfig.rewriteUser { root =>
            val config = AppConfig(id = newIdFromName(root, name), cachedName = Some(name), location = location)
            val newApps = root.applications.filterNot(_.location == config.location) :+ config
            root.copy(applications = newApps)
          } map { Unit =>
            RootConfig.user.applications.find(_.location == location) match {
              case Some(config) => Right(config)
              case None => Left("Somehow failed to save new app in config")
            }
          }
          case Left(error) =>
            Promise.successful(Left(error)).future
        }
      }

      result onComplete { _ =>
        sbt ! PoisonPill
      }

      result
    } else {
      Promise.successful(Left("Not a directory: " + location.getAbsolutePath())).future
    }
  }
}
