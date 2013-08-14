package controllers

import play.api.mvc.{ Action, Controller, WebSocket }
import java.io.File
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import snap.{ RootConfig, AppConfig, AppManager, Platform, DeathReportingProxy }
import activator.properties.ActivatorProperties
import play.Logger
import play.api.libs.iteratee.{ Iteratee, Enumerator }
import play.api.Play
import play.api.Mode
import java.util.concurrent.atomic.AtomicInteger
import activator.cache.TemplateMetadata

case class ApplicationModel(
  id: String,
  location: String,
  plugins: Seq[String],
  name: String,
  template: Option[String],
  recentApps: Seq[AppConfig],
  hasLocalTutorial: Boolean) {
}

object ApplicationModel {
  implicit val writes = Json.writes[ApplicationModel]
}

case class HomeModel(
  userHome: String,
  templates: Seq[TemplateMetadata],
  otherTemplateCount: Long,
  recentApps: Seq[AppConfig])

// Data we get from the new application form.
case class NewAppForm(
  name: String,
  location: String,
  template: String)

case class FromLocationForm(location: String)

// Here is where we detect if we're running at a given project...
object Application extends Controller {

  /**
   * Our index page.  Either we load an app from the CWD, or we direct
   * to the homepage to create a new app.
   */
  def index = Action {
    Async {
      AppManager.loadAppIdFromLocation(cwd) map {
        case activator.ProcessSuccess(name) => Redirect(routes.Application.app(name))
        case activator.ProcessFailure(errors) =>
          // TODO FLASH THE ERROR, BABY
          Redirect(routes.Application.forceHome)
      }
    }
  }

  import play.api.data._
  import play.api.data.Forms._
  /** The new application form on the home page. */
  val newAppForm = Form(
    mapping(
      "name" -> text,
      "location" -> text,
      "template" -> text)(NewAppForm.apply)(NewAppForm.unapply))

  /** Reloads the model for the home page. */
  private def homeModel = api.Templates.templateCache.metadata map { templates =>
    val tempSeq = templates.toSeq
    val featured = tempSeq filter (_.featured)
    val config = RootConfig.user
    HomeModel(
      userHome = ActivatorProperties.GLOBAL_USER_HOME,
      templates = featured,
      otherTemplateCount = tempSeq.length,
      recentApps = config.applications)
  }

  def redirectToApp(id: String) = Action {
    Redirect(routes.Application.app(id))
  }

  /** Loads the homepage, with a blank new-app form. */
  def forceHome = Action { implicit request =>
    Async {
      homeModel map { model =>
        Ok(views.html.home(model, newAppForm))
      }
    }
  }

  def test = Action { implicit request =>
    import Play.current
    if (Play.mode == Mode.Dev)
      Ok(views.html.test())
    else
      NotFound
  }

  /** Loads an application model and pushes to the view by id. */
  def app(id: String) = Action { implicit request =>
    Async {
      // TODO - Different results of attempting to load the application....
      Logger.debug("Loading app for /app html page")
      // we kill off any previous browser tab
      AppManager.loadTakingOverApp(id) map { theApp =>
        Logger.debug(s"loaded for html page: ${theApp}")
        Ok(views.html.application(getApplicationModel(theApp)))
      } recover {
        case e: Exception =>
          // TODO we need to have an error message and "flash" it then
          // display it on home screen
          Logger.error("Failed to load app id " + id + ": " + e.getMessage(), e)
          Redirect(routes.Application.forceHome)
      }
    }
  }

  private def connectionStreams(id: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    Logger.debug(s"Computing connection streams for app ID $id")
    val streamsFuture = AppManager.loadApp(id) flatMap { app =>
      Logger.debug(s"Loaded app for connection: $app")
      // this is just easier to debug than a timeout; it isn't reliable
      if (app.actor.isTerminated) throw new RuntimeException("App is dead")

      import snap.WebSocketActor.timeout
      DeathReportingProxy.ask(app.system, app.actor, snap.CreateWebSocket).map {
        case snap.WebSocketAlreadyUsed =>
          Logger.warn("web socket already in use for $app")
          throw new RuntimeException("can only open apps in one tab at a time")
        case whatever =>
          Logger.debug(s"CreateWebSocket resulted in $whatever")
          whatever
      }.mapTo[(Iteratee[JsValue, _], Enumerator[JsValue])].map { streams =>
        Logger.debug("WebSocket streams created")
        streams
      }
    }

    streamsFuture onFailure {
      case e: Throwable =>
        Logger.info(s"WebSocket failed to open: ${e.getClass.getName}: ${e.getMessage}")
    }

    streamsFuture
  }

  /**
   * Connects from an application page to the "stateful" actor/server we use
   * per-application for information.
   */
  def connectApp(id: String) = WebSocket.async[JsValue] { request =>
    Logger.debug("Connect request for app id: " + id)
    val streamsFuture = snap.Akka.retryOverMilliseconds(2000)(connectionStreams(id))

    streamsFuture onFailure {
      case e: Throwable =>
        Logger.warn(s"Giving up on opening websocket")
    }

    streamsFuture
  }

  /** List all the applications in our history as JSON. */
  def getHistory = Action { request =>
    Ok(Json.toJson(RootConfig.user.applications))
  }

  /**
   * Returns the application model (for rendering the page) based on
   * the current snap App.
   */
  def getApplicationModel(app: snap.App) =
    ApplicationModel(
      app.config.id,
      Platform.getClientFriendlyFilename(app.config.location),
      // TODO - These should be drawn from the template itself...
      Seq("plugins/home/home", "plugins/code/code", "plugins/compile/compile", "plugins/run/run", "plugins/test/test"),
      app.config.cachedName getOrElse app.config.id,
      // TODO - something less lame than exception here...
      app.templateID,
      RootConfig.user.applications,
      hasLocalTutorial(app))

  def hasLocalTutorial(app: snap.App): Boolean = {
    val tutorialConfig = new java.io.File(app.config.location, activator.cache.Constants.METADATA_FILENAME)
    tutorialConfig.exists
  }

  def appTutorialFile(id: String, location: String) = Action { request =>
    Async {
      AppManager.loadApp(id) map { theApp =>
        // If we're debugging locally, pull the local tutorial, otherwise redirect
        // to the templates tutorial file.
        if (hasLocalTutorial(theApp)) {
          // TODO - Don't hardcode tutorial directory name!
          val localTutorialDir = new File(theApp.config.location, "tutorial")
          val file = new File(localTutorialDir, location)
          if (file.exists) Ok sendFile file
          else NotFound
        } else theApp.templateID match {
          case Some(template) => Redirect(api.routes.Templates.tutorial(template, location))
          case None => NotFound
        }
      } recover {
        case e: Exception =>
          // TODO we need to have an error message and "flash" it then
          // display it on home screen
          Logger.error("Failed to find tutorial app id " + id + ": " + e.getMessage(), e)
          NotFound
      }
    }
  }

  val homeActorCount = new AtomicInteger(1)

  /** Opens a stream for home events. */
  def homeStream =
    snap.WebSocketActor.create(snap.Akka.system, new snap.HomePageActor, "home-socket-" + homeActorCount.getAndIncrement())

  /** The current working directory of the app. */
  val cwd = (new java.io.File(".").getAbsoluteFile.getParentFile)
}
