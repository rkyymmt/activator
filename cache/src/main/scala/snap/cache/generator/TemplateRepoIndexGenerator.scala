package snap
package cache
package generator

import java.io.File

/**
 * This class is responsible for constructing a new Template Repository
 *  index from set of local template files.
 */
object TemplateRepoIndexGenerator {

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      System.err.println("Usage:  TemplateRepoIndexGenerator <local repo>")
      System.exit(1)
    }
    val localRepo = new java.io.File(args(0))
    makeMetaDataIndex(localRepo)
  }

  def makeMetaDataIndex(localRepo: File)(implicit reader: MetadataReader, indexProvider: IndexDbProvider): File = {
    // TODO - Keep these relative file location magiks in one class
    val metadata = collectTemplateMetadata(localRepo)
    val indexDir = new File(localRepo, "index.db")
    // Make sure we clear out the previous index.
    if (indexDir.exists) IO.delete(indexDir)
    val writer = indexProvider.write(indexDir)
    try {
      for (item <- metadata) {
        // TODO - If verbose...
        println(s"Adding template to repository index: ${item.id} - ${item.name}")
        writer.insert(item)
      }
    } finally writer.close()
    indexDir
  }

  private def collectTemplateMetadata(localRepo: File)(implicit reader: MetadataReader): Seq[TemplateMetadata] =
    for {
      child <- Option(localRepo.listFiles) getOrElse Array.empty
      if child.isDirectory
      // TODO - Store this filename in one location!
      metadataFile = new File(child, "activator.properties")
      if metadataFile.exists
      metadata <- reader.read(metadataFile).toSeq
    } yield metadata
}
