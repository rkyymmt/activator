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
      val md = java.security.MessageDigest.getInstance("SHA-1")
      md.update(user.name.getBytes)
      md.update(user.title.getBytes)
      md.update(user.description.getBytes)
      md.update(user.tags.mkString(",").getBytes)
      convertToHex(md.digest)
    }
    // Note: This marks the third time I've copied this method...
    // We may want to just make a "Hash helper" library (or rip the one from
    // dbuild).   If we ever want a good BigData story, we need to focus on
    // good hash algorithms and fast ways to hash data/case-classes.
    //  <end of rant>
    private def convertToHex(data: Array[Byte]): String = {
      val buf = new StringBuffer
      def byteToHex(b: Int) =
        if ((0 <= b) && (b <= 9)) ('0' + b).toChar
        else ('a' + (b - 10)).toChar
      for (i <- 0 until data.length) {
        buf append byteToHex((data(i) >>> 4) & 0x0F)
        buf append byteToHex(data(i) & 0x0F)
      }
      buf.toString
    }
  }
}
