package snap
package cache
package generator

/**
 * This class is responsible
 *  for taking user-defined metadata and completing it with derived metadata.
 */
trait MetadataCompleter {
  def complete(user: AuthorDefinedTemplateMetadata): IndexStoredTemplateMetadata
}

object MetadataCompleter {
  implicit object default extends MetadataCompleter {
    def complete(user: AuthorDefinedTemplateMetadata): IndexStoredTemplateMetadata =
      IndexStoredTemplateMetadata(
        id = makeId(user),
        timeStamp = currentTimestamp,
        featured = true,
        usageCount = None, // TODO - pull form server
        userConfig = user)

    private def currentTimestamp: Long = {
      // TODO - user a better method to get timestamp here...
      System.currentTimeMillis
    }
    // To make a UUID, we just SHA the thing
    private def makeId(user: AuthorDefinedTemplateMetadata): String = {
      // Here we decide our hashing algorithm.
      import hashing.Hash.default._
      import hashing.hash
      hash(user)
    }
  }
}
