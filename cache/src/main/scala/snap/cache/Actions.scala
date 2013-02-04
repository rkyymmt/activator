package snap
package cache

import properties.SnapProperties
import java.io.File

/**
 * this class contains all template cache actions for both the UI and the console-based
 * methods.  We put it here for easier testing (at least we hope we can get easier testing).
 */
object Actions {

  // This method will clone a template to a given location
  def cloneTemplate(cache: TemplateCache, id: String, location: java.io.File): Unit = {
    // TODO - Use a real error handling/validation library instead of exceptions.
    val template = cache template id
    if (!template.isDefined) sys.error(s"Template ${id} not found")
    // If location doesn't exist, let's create it.
    // TODO - Is this ok?  This may throw an exception...
    if (!location.exists) IO createDirectory location

    //  Copy all files from the template dir.
    // TODO - Use SBT IO when it's available.
    for {
      t <- template
      (file, path) <- t.files
      to = new java.io.File(location, path)
    } if (file.isDirectory) snap.cache.IO.createDirectory(to)
    else snap.cache.IO.copyFile(file, to)

    // Write necessary IDs to the properties file!
    val propsFile = new File(location, "project/build.properties")
    IO.createDirectory(propsFile.getParentFile)
    // TODO - Force sbt version?
    updateProperties(propsFile,
      Map(
        SnapProperties.BLUEPRINT_UUID_PROPERTY_NAME -> id,
        SnapProperties.SNAP_ABI_VERSION_PROPERTY_NAME -> SnapProperties.APP_ABI_VERSION))
    // TODO - Capture errors and return a nicer message...
    ()
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