package snap

import scala.concurrent.Future
import java.io.File
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Promise
import akka.pattern._
import com.typesafe.sbtrc.launching.SbtProcessLauncher
import com.typesafe.sbtrc.DefaultSbtProcessFactory
import com.typesafe.sbtrc.protocol
import play.Logger
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.actor._
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.json.JsObject
import java.util.concurrent.atomic.AtomicInteger
import scala.util.control.NonFatal
import activator._

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
            // this should be "instant" but 5 seconds to be safe
            val app = Await.result(futureApp, 5.seconds)
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
            log.debug(s"returning existing app from app cache for $id")
            f map { a =>
              log.debug(s"existing app $a terminated=${a.actor.isTerminated}")
              GotApp(a)
            } pipeTo sender
          case None => {
            val appFuture: Future[snap.App] = RootConfig.user.applications.find(_.id == id) match {
              case Some(config) =>
                val app = new snap.App(config, snap.Akka.system, AppManager.sbtChildProcessMaker)
                log.debug(s"creating a new app for $id, $app")
                Promise.successful(app).future
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

sealed trait KeepAliveRequest
case class RegisterKeepAlive(ref: ActorRef) extends KeepAliveRequest
case class CheckForExit(serial: Int) extends KeepAliveRequest

// Note: we only CheckForExit on the transition from
// 1 to 0 keep alives, so we do not exit just because
// no keep alive has ever been added. This means there is
// infinite time on startup to wait for a browser tab to
// be opened.
class KeepAliveActor extends Actor with ActorLogging {
  var keepAlives = Set.empty[ActorRef]
  // this increments on keepAlives mutation, allowing us to decide
  // whether a CheckForExit is still valid or should be dropped
  var serial = 0

  override def receive = {
    case Terminated(ref) =>
      log.debug("terminated {}", ref)
      if (keepAlives.contains(ref)) {
        log.debug("Removing ref from keep alives {}", ref)
        keepAlives -= ref
        serial += 1
      } else {
        log.warning("Ref was not in the keep alives set {}", ref)
      }
      if (keepAlives.isEmpty) {
        log.debug("scheduling CheckForExit")
        context.system.scheduler.scheduleOnce(60.seconds, self, CheckForExit(serial))
      }
    case req: KeepAliveRequest => req match {
      case RegisterKeepAlive(ref) =>
        if (ref.isTerminated) {
          log.debug("ref already terminated so won't keep us alive {}", ref)
        } else {
          log.debug("Actor will keep us alive {}", ref)
          keepAlives += ref
          serial += 1
          context.watch(ref)
        }
      case CheckForExit(validitySerial) =>
        if (validitySerial == serial) {
          log.debug("checking for exit, keepAlives={}", keepAlives)
          if (keepAlives.isEmpty) {
            log.info("Activator doesn't seem to be open in any browser tabs, so shutting down.")
            self ! PoisonPill
          }
        } else {
          log.debug("Something changed since CheckForExit scheduled, disregarding")
        }
    }
  }

  override def postStop() {
    log.debug("postStop")
    log.info("Exiting.")
    // TODO - Not in debug mode
    val debugMode = sys.props.get("activator.runinsbt").map(_ == "true").getOrElse(false)
    if (!debugMode) System.exit(0)
    else log.info("Would have killed activator if we weren't in debug mode.")
  }
}

object AppManager {

  // this is supposed to be set by the main() launching the UI.
  // If not, we know we're running inside the build and we need
  // to use the default "Debug" version.
  def sbtChildProcessMaker: SbtProcessLauncher = Global.sbtProcessLauncher

  private val keepAlive = snap.Akka.system.actorOf(Props(new KeepAliveActor), name = "keep-alive")

  def registerKeepAlive(ref: ActorRef): Unit = {
    keepAlive ! RegisterKeepAlive(ref)
  }

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

  // If the app actor is already in use, disconnect
  // it and make a new one.
  def loadTakingOverApp(id: String): Future[snap.App] = {
    Akka.retryOverMilliseconds(4000) {
      loadApp(id) flatMap { app =>
        implicit val timeout = akka.util.Timeout(5.seconds)
        DeathReportingProxy.ask(Akka.system, app.actor, GetWebSocketCreated) map {
          case WebSocketCreatedReply(created) =>
            if (created) {
              Logger.debug(s"browser tab already connected to $app, disconnecting it")
              app.actor ! PoisonPill
              throw new Exception("retry after killing app actor")
            } else {
              Logger.debug(s"app looks shiny and new! $app")
              app
            }
        }
      }
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
      val factory = new DefaultSbtProcessFactory(location, sbtChildProcessMaker)
      // TODO - we should actually have ogging of this sucker
      factory.init(akka.event.NoLogging)
      val sbt = factory.newChild(snap.Akka.system)
      implicit val timeout = Akka.longTimeoutThatIsAProblem;

      val requestManager = snap.Akka.system.actorOf(
        Props(new RequestManagerActor("learn-project-name", sbt, false)({
          event =>
            eventHandler foreach (_ apply event)
        })), name = "request-manager-" + requestManagerCount.getAndIncrement())
      val resultFuture: Future[ProcessResult[AppConfig]] =
        (requestManager ? protocol.NameRequest(sendEvents = true)) map {
          case protocol.NameResponse(name, _) => {
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
