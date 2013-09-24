package activator

import xsbti.{ AppMain, AppConfiguration }
import activator.properties.ActivatorProperties._
import java.io.File

/** Expose for SBT launcher support. */
class ActivatorLauncher extends AppMain {

  def run(configuration: AppConfiguration) =
    // TODO - Detect if we're running against a local project.
    try configuration.arguments match {
      case Array("ui") => RebootToUI(configuration)
      case Array("new") => Exit(ActivatorCli(configuration))
      case Array("shell") => RebootToSbt(configuration, useArguments = false)
      case _ if Sbt.looksLikeAProject(new File(".")) => RebootToSbt(configuration, useArguments = true)
      case _ => displayHelp(configuration)
    } catch {
      case e: Exception => generateErrorReport(e)
    }
  // Wrapper to return exit codes.
  case class Exit(val code: Int) extends xsbti.Exit

  def displayHelp(configuration: AppConfiguration) = {
    System.err.println(s"""| Did not detect an ${SCRIPT_NAME} project in this directory.
                          |
                          | There are three ways to run ${SCRIPT_NAME}:
                          |
                          | 1. Recommended: try `${SCRIPT_NAME} ui` to create a project in the UI
                          | 2. Use `${SCRIPT_NAME} new` to create a project on the command line
                          | 3. Load an existing project by re-running ${SCRIPT_NAME} in a project directory
                          |""".stripMargin)
    Exit(1)
  }

  def generateErrorReport(e: Exception) = {
    // TODO - Make a real error report.
    e.printStackTrace()
    Exit(2)
  }
}
/**
 * If we're rebooting into a non-cross-versioned app, we can leave off the scala
 *  version declaration, and Ivy will figure it out for us.
 */
trait AutoScalaReboot extends xsbti.Reboot {
  def scalaVersion = null
}

// Wrapper to return the UI application.
case class RebootToUI(configuration: AppConfiguration) extends AutoScalaReboot {
  val arguments = Array.empty[String]
  val baseDirectory = configuration.baseDirectory
  val app = ApplicationID(
    groupID = configuration.provider.id.groupID,
    // TODO - Pull this string from somewhere else so it's only configured in the build?
    name = "activator-ui",
    version = APP_VERSION,
    mainClass = "activator.UIMain")
}

// Wrapper to reboot into SBT.
// TODO - See if we can just read the configuration from the boot properties of sbt itself...
// TODO - This doesn't handle sbt < 0.12
case class RebootToSbt(configuration: AppConfiguration, useArguments: Boolean = false) extends AutoScalaReboot {

  val arguments = if (useArguments) configuration.arguments else Array.empty[String]
  val baseDirectory = configuration.baseDirectory
  val app = ApplicationID(
    groupID = "org.scala-sbt",
    name = "sbt",
    // TODO - Pull sbt version from file...
    version = RebootToSbt.determineSbtVersion(baseDirectory),
    mainClass = "sbt.xMain",
    mainComponents = Array("xsbti", "extra"))
}
object RebootToSbt {
  def determineSbtVersion(baseDirectory: File): String = {
    try {
      val buildPropsFile = new java.io.File(baseDirectory, "project/build.properties")
      val props = new java.util.Properties
      sbt.IO.load(props, buildPropsFile)
      props.getProperty("sbt.version", SBT_DEFAULT_VERSION)
    } catch {
      case e: java.io.IOException =>
        // TODO - Should we error out here, or just default?  For now, just default....
        System.err.println("WARNING:  Could not read build.properties file.  Defaulting sbt version to " + SBT_DEFAULT_VERSION + ".  \n  Reason: " + e.getMessage)
        SBT_DEFAULT_VERSION
    }

  }
}
// Helper class to make using ApplicationID in xsbti easier.
case class ApplicationID(
  groupID: String,
  name: String,
  version: String,
  mainClass: String,
  mainComponents: Array[String] = Array("xsbti"),
  crossVersioned: Boolean = false,
  crossVersionedValue: xsbti.CrossValue = xsbti.CrossValue.Disabled,
  classpathExtra: Array[java.io.File] = Array.empty) extends xsbti.ApplicationID
