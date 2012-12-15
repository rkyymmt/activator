package snap

import xsbti.{AppMain, AppConfiguration}

/** Expose for SBT launcher support. */
class SnapLauncher extends AppMain {

  def run(configuration: AppConfiguration) =
    // TODO - Detect non-interactive terminal
    try configuration.arguments match {
      case Array("ui") => RebootToUI(configuration)
      case _           => RebootToSbt(configuration)
    } catch {
      case e: Exception =>
        // TODO - Generate some kind of error report here.
        Exit(1)
    }
  // Wrapper to return exit codes.
  case class Exit(val code: Int) extends xsbti.Exit
}

// Wrapper to return the UI application.
// TODO - Generate this via SBT code, so the hard-coded settings come
// from the build.
case class RebootToUI(configuration: AppConfiguration) extends xsbti.Reboot {
  val arguments = Array.empty[String]
  val baseDirectory = configuration.baseDirectory
  val scalaVersion = "2.10.0-RC1"
  val app = ApplicationID(
              groupID = configuration.provider.id.groupID,
              name = "snap-ui",
              version = snap.properties.SnapProperties.APP_VERSION,
              mainClass = "snap.UIMain"
            )
}
// Wrapper to reboot into SBT.
// TODO - Generate this via the SBT build code, so the hardcoded SBT version
// lives in one spot.
// OR we can even detect the SBT version...
case class RebootToSbt(configuration: AppConfiguration) extends xsbti.Reboot {
  val arguments = configuration.arguments
  val baseDirectory = configuration.baseDirectory
  val scalaVersion = "2.9.2"
  val app = ApplicationID(
              groupID = "org.scala-sbt",
              name = "sbt",
              version = snap.properties.SnapProperties.SBT_VERSION,
              mainClass = "sbt.xMain",
              mainComponents = Array("xsbti", "extra")
            )
}

// Helper class to make using ApplicationID in xsbti easier.
case class ApplicationID(
  groupID: String,
  name: String,
  version: String,
  mainClass: String,
  mainComponents: Array[String] = Array("xsbti"),
  crossVersioned: Boolean = false,
  classpathExtra: Array[java.io.File] = Array.empty
) extends xsbti.ApplicationID
