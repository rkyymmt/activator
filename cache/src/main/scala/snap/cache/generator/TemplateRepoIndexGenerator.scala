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
    val indexDir = makeMetaDataIndex(localRepo)
    // Now we make the Zip file and the hash properties.
    val indexZipFile = makeMetadataZipFile(indexDir, new File(localRepo, Constants.METADATA_INDEX_FILENAME + ".zip"))
    val cacheProps = new CacheProperties(new File(localRepo, Constants.CACHE_PROPS_FILENAME))
    cacheProps.cacheIndexHash = snap.hashing.hash(indexZipFile)
    cacheProps.save()
  }

  def makeMetaDataIndex(localRepo: File)(implicit reader: MetadataReader,
    indexProvider: IndexDbProvider,
    completer: MetadataCompleter): File = {
    // TODO - Keep these relative file location magiks in one class
    val metadata = collectTemplateMetadata(localRepo)
    val indexDir = new File(localRepo, Constants.METADATA_INDEX_FILENAME)
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

  def makeMetadataZipFile(indexDir: File, zipFile: File): File = {
    // We construct a traversable that walks the directory looking for files to include in the zip.
    object zipFiles extends Traversable[(File, String)] {
      def foreach[U](f: ((File, String)) => U): Unit = {
        // Now we traverse everything:
        def traverse(path: String, file: File): Unit = {
          val currentPath = s"${path}/${file.getName}"
          f(file -> currentPath)
          if (file.isDirectory) {
            val children = IO.listFiles(file)
            // TODO - Make tail-recursive?
            children.foreach(traverse(currentPath, _))
          }
        }
        traverse(indexDir.getName, indexDir)
      }
    }
    IO.zip(zipFiles, zipFile)
    zipFile
  }

  private def collectTemplateMetadata(localRepo: File)(implicit reader: MetadataReader,
    completer: MetadataCompleter): Seq[IndexStoredTemplateMetadata] =
    for {
      child <- Option(localRepo.listFiles) getOrElse Array.empty
      if child.isDirectory
      // TODO - Store this filename in one location!
      metadataFile = new File(child, Constants.METADATA_FILENAME)
      if metadataFile.exists
      metadata <- reader.read(metadataFile).toSeq
    } yield completer complete metadata
}
