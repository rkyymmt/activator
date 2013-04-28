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
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.json.JsObject
import java.util.concurrent.atomic.AtomicInteger
import scala.util.control.NonFatal

sealed trait AppCacheRequest

case class GetApp(id: String) extends AppCacheRequest
case object Cleanup extends AppCacheRequest

sealed trait AppCacheReply

case class GotApp(app: snap.App) extends AppCacheReply

class AppCacheActor extends Actor with ActorLogging {
  var appCache: Map[String, Future[snap.App]] = Map.empty

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private def cleanup(deadRef: Option[ActorRef]): Unit = {
    appCache = appCache.filter {
      case (id, futureApp) =>
        if (futureApp.isCompleted) {
          try {
            val app = Await.result(futureApp, 2.seconds)
            if (Some(app.actor) == deadRef) {
              log.debug("cleaning up terminated app actor {} {}", id, app.actor)
              false
            } else if (app.actor.isTerminated) {
              // as side effect, mop up anything accidentally left over
              log.warning("leftover app wasn't cleaned up before {} {}", id, app.actor)
              false
            } else {
              true
            }
          } catch {
            case e: Exception =>
              log.warning("cleaning up app {} which failed to load due to '{}'", id, e.getMessage)
              false
          }
        } else {
          // still pending, keep it
          true
        }
    }
  }

  override def receive = {
    case Terminated(ref) =>
      cleanup(Some(ref))

    case req: AppCacheRequest => req match {
      case GetApp(id) =>
        appCache.get(id) match {
          case Some(f) =>
            f.map(GotApp(_)).pipeTo(sender)
          case None => {
            val appFuture: Future[snap.App] = RootConfig.user.applications.find(_.id == id) match {
              case Some(config) =>
                Promise.successful(new snap.App(config, snap.Akka.system, AppManager.sbtChildProcessMaker)).future
              case whatever =>
                Promise.failed(new RuntimeException("No such app with id: '" + id + "'")).future
            }
            appCache += (id -> appFuture)

            // set up to watch the app's actor, or forget the future
            // if the app is never created
            appFuture.onComplete { value =>
              value.foreach { app =>
                context.watch(app.actor)
              }
              if (value.isFailure)
                self ! Cleanup
            }

            appFuture.map(GotApp(_)).pipeTo(sender)
          }
        }
      case Cleanup =>
        cleanup(None)
    }
  }

  override def postStop() = {
    log.debug("postStop")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    log.debug("preRestart {} {}", reason, message)
  }
}

object AppManager {

  // this is supposed to be set by the main() launching the UI.
  // If not, we know we're running inside the build and we need
  // to use the default "Debug" version.
  @volatile var sbtChildProcessMaker: SbtChildProcessMaker = DebugSbtChildProcessMaker

  val appCache = snap.Akka.system.actorOf(Props(new AppCacheActor), name = "app-cache")

  val requestManagerCount = new AtomicInteger(1)

  // Loads an application based on its id.
  // This needs to look in the RootConfig for the App/Location
  // based on this ID.
  // If the app id does not exist ->
  //    Return error
  // If it exists
  //    Return the app
  def loadApp(id: String): Future[snap.App] = {
    implicit val timeout = Akka.longTimeoutThatIsAProblem
    (appCache ? GetApp(id)).map {
      case GotApp(app) => app
    }
  }

  // Loads the ID of an app based on the CWD.
  // If we don't have an ID in RootConfig for this location, then
  // - we should load the app and determine a good id
  // - we should store the id/location in the RootConfig
  // - We should return the new ID or None if this location is not an App.
  def loadAppIdFromLocation(location: File, eventHandler: Option[JsObject => Unit] = None): Future[ProcessResult[String]] = {
    val absolute = location.getAbsoluteFile()
    RootConfig.user.applications.find(_.location == absolute) match {
      case Some(app) => Promise.successful(ProcessSuccess(app.id)).future
      case None => {
        doInitialAppAnalysis(location, eventHandler) map { _.map(_.id) }
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
  private def doInitialAppAnalysis(location: File, eventHandler: Option[JsObject => Unit] = None): Future[ProcessResult[AppConfig]] = {
    val validated = ProcessSuccess(location).validate(
      Validation.isDirectory,
      Validation.looksLikeAnSbtProject)

    validated flatMapNested { location =>
      // NOTE -> We have to use the factory to ensure that shims are installed BEFORE we try to load the app.
      // While we should consolidate all sbt specific code, right now the child factory is the correct entry point.
      val factory = new DefaultSbtChildFactory(location, sbtChildProcessMaker)
      // TODO - we should actually have ogging of this sucker
      factory.init(akka.event.NoLogging)
      val sbt = factory.newChild(snap.Akka.system)
      implicit val timeout = Timeout(60, TimeUnit.SECONDS)

      val requestManager = snap.Akka.system.actorOf(
        Props(new RequestManagerActor("learn-project-name", sbt, false)({
          event =>
            eventHandler foreach (_ apply event)
        })), name = "request-manager-" + requestManagerCount.getAndIncrement())
      val resultFuture: Future[ProcessResult[AppConfig]] =
        (requestManager ? protocol.NameRequest(sendEvents = true)) map {
          case protocol.NameResponse(name) => {
            Logger.info("sbt told us the name is: '" + name + "'")
            name
          }
          case protocol.ErrorResponse(error) =>
            // here we need to just recover, because if you can't open the app
            // you can't work on it to fix it
            Logger.info("error getting name from sbt: " + error)
            val name = location.getName
            Logger.info("using file basename as app name: " + name)
            name
        } flatMap { name =>
          RootConfig.rewriteUser { root =>
            val config = AppConfig(id = newIdFromName(root, name), cachedName = Some(name), location = location)
            val newApps = root.applications.filterNot(_.location == config.location) :+ config
            root.copy(applications = newApps)
          } map { Unit =>
            import ProcessResult.opt2Process
            RootConfig.user.applications.find(_.location == location)
              .validated(s"Somehow failed to save new app at ${location.getPath} in config")
          }
        }
      resultFuture onComplete { result =>
        Logger.debug(s"Stopping sbt child because we got our app config or error ${result}")
        sbt ! PoisonPill
      }
      // change a future-with-exception into a future-with-value
      // where the value is a ProcessFailure
      resultFuture recover {
        case NonFatal(e) =>
          ProcessFailure(e)
      }
    }
  }

  def onApplicationStop() = {
    Logger.warn("AppManager onApplicationStop is disabled pending some refactoring so it works with FakeApplication in tests")
    //Logger.debug("Killing app cache actor onApplicationStop")
    //appCache ! PoisonPill
  }
}
