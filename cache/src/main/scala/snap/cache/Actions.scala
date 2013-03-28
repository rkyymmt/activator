package snap
package cache

import snap.IO
import _root_.builder.properties.BuilderProperties._
import java.io.File
/**
 * this class contains all template cache actions for both the UI and the console-based
 * methods.  We put it here for easier testing (at least we hope we can get easier testing).
 */
object Actions {

  // This method will clone a template to a given location
  def cloneTemplate(cache: TemplateCache, id: String, location: java.io.File): ProcessResult[Unit] =
    for {
      template <- Validating(cache template id getOrElse sys.error(s"Template ${id} not found"))
      _ <- Validating.withMsg("Failred to create $location") {
        if (!location.exists) IO createDirectory location
      }
      _ <- Validating.withMsg("Failed to copy template") {
        for {
          (file, path) <- template.files
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
            BUILDER_ABI_VERSION_PROPERTY_NAME -> APP_ABI_VERSION))
      }
    } yield ()

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
