package snap
package cache

import snap.IO
import _root_.activator.properties.ActivatorProperties._
import java.io.File
import java.util.regex.Matcher
import java.io.FileNotFoundException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

/**
 * this class contains all template cache actions for both the UI and the console-based
 * methods.  We put it here for easier testing (at least we hope we can get easier testing).
 */
object Actions {
  private def stringLiteral(s: String): String = {
    // sorry!
    "\"\"\"" + s.replaceAllLiterally("\"\"\"", "\"\"\"" + """+ "\"\"\"" + """ + "\"\"\"") + "\"\"\""
  }

  // this does nothing if it can't figure out how to perform the rename,
  // but should throw if a rename looks possible but fails due to e.g. an IO error
  private def bestEffortRename(basedir: java.io.File, projectName: String): Unit = {
    for (
      file <- Seq(
        new File(basedir, "build.sbt"),
        new File(basedir, "project/Build.scala"))
        .filter(_.exists)
        .headOption
    ) {
      val contents = IO.slurp(file)

      val modified = contents.replaceFirst("(name[ \t]*:=[ \t]*)\"[^\"]+\"", "$1" + Matcher.quoteReplacement(stringLiteral(projectName)));
      if (modified != contents) {
        IO.write(file, modified)
      }
    }
  }

  // This method will clone a template to a given location
  def cloneTemplate(
    cache: TemplateCache,
    id: String,
    location: java.io.File,
    projectName: Option[String],
    filterMetadata: Boolean = true)(
      implicit ctx: ExecutionContext): Future[ProcessResult[Unit]] =
    (cache template id) map { templateOpt =>
      for {
        template <- Validating(templateOpt getOrElse sys.error(s"Template ${id} not found"))
        _ <- Validating.withMsg(s"Failed to create $location") {
          if (!location.exists) IO createDirectory location
        }
        _ <- Validating.withMsg("Failed to copy template") {
          for {
            (file, path) <- template.files
            // TODO - We should rethink how this guy is generated.
            // Probably just generate him directly from metadata case class
            // and without an ID or timestamp?
            if !filterMetadata || (path != Constants.METADATA_FILENAME)
            to = new java.io.File(location, path)
          } if (file.isDirectory) IO.createDirectory(to)
          else {
            IO.copyFile(file, to)
            if (file.canExecute) to.setExecutable(true)
          }
        }
        _ <- Validating.withMsg("Failed to update property file") {
          // Write necessary IDs to the properties file!
          val propsFile = new File(location, "project/build.properties")
          IO.createDirectory(propsFile.getParentFile)
          // TODO - Force sbt version?
          updateProperties(propsFile,
            Map(
              TEMPLATE_UUID_PROPERTY_NAME -> id,
              ABI_VERSION_PROPERTY_NAME -> APP_ABI_VERSION))
        }
        _ <- Validating.withMsg("Failed to rename project") {
          projectName.foreach(bestEffortRename(location, _))
        }
      } yield ()
    }

  private def updateProperties(propsFile: File, newProps: Map[String, String]): Unit = {
    val props = IO loadProperties propsFile
    // Updated props
    for {
      (key, value) <- newProps
    } props setProperty (key, value)
    // Write props
    IO storeProperties (propsFile, props)
  }
}
