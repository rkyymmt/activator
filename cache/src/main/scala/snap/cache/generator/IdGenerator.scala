package snap
package cache
package generator

import java.io.File

/**
 * This object is responsible for reading a template directory and determining the ID of that directory
 *  where we should copy it, when generating a local template repository...
 */
object IdGenerator {
  def generateId(dir: File): String =
    getId(dir)

  // This grabs the auto-generated id for a piece of template metadata.  Note: unfortunately, it will also
  // complete the meatda right now (i.e. pull stats from typesafe.com).
  def getId(templateDir: File)(implicit reader: MetadataReader, completer: MetadataCompleter): String = {
    reader.read(new File(templateDir, Constants.METADATA_FILENAME)) match {
      case Some(meta) => completer.complete(meta).id
      case None => sys.error("Could not read template metadata!")
    }

  }
}
