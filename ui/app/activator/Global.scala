package activator

import play.api._
import com.typesafe.sbtrc.launching.{
  SbtProcessLauncher,
  DebugSbtProcessLauncher
}
import scala.annotation.implicitNotFound

object Global extends GlobalSettings {

  // Here we store the active sbt process launcher.
  @volatile private var _sbtProcessLauncher: Option[SbtProcessLauncher] = None

  // We need to install the default process launcher from the sbt-main
  // method so we have its application config.
  def installSbtLauncher(launcher: SbtProcessLauncher): Unit =
    _sbtProcessLauncher = Some(launcher)

  // Returns the currently registered process launcher, or
  // the testing one, since we're really lazy and uncordinated in removing global state.
  // We actually run tests without this having initialized, or manually initializing
  // our application ourselves, so it's bad.
  def sbtProcessLauncher: SbtProcessLauncher =
    _sbtProcessLauncher.getOrElse(DebugSbtProcessLauncher)

  // Before we start up, we should check to see if we need a process launcher.
  // However, in prod mode, we just blow up.
  override def beforeStart(app: Application) {
    app.mode match {
      case play.api.Mode.Prod => // Ignore, we should have a value from the launcher.
      case _ =>
        // In testing mode, we use the debug launcher
        _sbtProcessLauncher = Some(DebugSbtProcessLauncher)
    }
  }

  override def onStop(app: Application) {
    super.onStop(app)
    Logger.info("onStop received closing down the app")
    snap.AppManager.onApplicationStop()
  }
}
