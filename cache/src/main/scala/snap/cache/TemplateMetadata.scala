package snap.cache

/**
 * This represents the metadata information that AUTHORS of Templates will create for us
 */
case class AuthorDefinedTemplateMetadata(
  name: String, // Url/CLI-friendly name of the template
  title: String, // Verbose and fun name of the template, used in GUI.
  description: String, // A long-winded description about what this template does.
  tags: Seq[String] // A set of folksonomy tags describing what's in this template, used for searching.
  )
object AuthorDefinedTemplateMetadata {
  /** Default hash for generating ids. */
  implicit object Hash extends snap.hashing.MessageDigestHasher[AuthorDefinedTemplateMetadata]("SHA-1") {
    protected def updateDigest(t: AuthorDefinedTemplateMetadata, md: java.security.MessageDigest): Unit = {
      md.update(t.name.getBytes)
      md.update(t.title.getBytes)
      md.update(t.description.getBytes)
      md.update(t.tags.mkString(",").getBytes)
    }
  }
}

/**
 * This represents metadata information stored in the local template repository.  This includes all
 * static, searchable and identifying information for a template.
 */
case class IndexStoredTemplateMetadata(
  id: String,
  userConfig: AuthorDefinedTemplateMetadata,
  timeStamp: Long,
  featured: Boolean, // Display on the home page.
  usageCount: Option[Long] // Usage counts pulled from website.
  ) {
  def name = userConfig.name
  def title = userConfig.title
  def description = userConfig.description
  def tags = userConfig.tags
}

/**
 * This represents the TempalteMetadata as returned by a local repostiory and useful for the GUI.
 */
case class TemplateMetadata(
  persistentConfig: IndexStoredTemplateMetadata,
  locallyCached: Boolean // Denotes whether or not the template has been fully downloaded, or if only the metadata is in the cache.
  ) {
  // TODO - update equality/hashcode to be based on *JUST* the  ID
  def id = persistentConfig.id
  def name = persistentConfig.name
  def title = persistentConfig.title
  def description = persistentConfig.description
  def tags = persistentConfig.tags
  def timeStamp = persistentConfig.timeStamp
  def featured = persistentConfig.featured
  def usageCount = persistentConfig.usageCount
}
