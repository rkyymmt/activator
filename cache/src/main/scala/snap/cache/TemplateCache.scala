package snap.cache


import snap.properties.SnapProperties

// TODO - This may need more work.
// TODO - We probably are on the limit of fields for a useful case-class....
case class TemplateMetadata(id: String,
                             name: String,
                             version: String,
                             description: String,
                             publisher: String,
                             publishDate: String,
                             tags: Seq[String]) {
  // TODO - update equality/hashcode to be based on ID
}
case class Template(metadata: TemplateMetadata,
                     files: Seq[(java.io.File, String)])   // TODO - What do we need for help?

/** This interface represents the template cache within SNAP.  it's your mechanisms to find things and 
 *  create stuff.
 */
trait TemplateCache {
  /** Find a template within the cache. */
  def template(id: String): Option[Template]
  /** Search for a template within the cache. */
  def search(query: String): Iterable[TemplateMetadata]
  /** Returns all metadata we have for templates. */
  def metadata: Iterable[TemplateMetadata]
}

object TemplateCache {
  /** Creates a default template cache, with stubbed out everything for now. */
  def apply(): TemplateCache = new DemoTemplateCache()
}


// This class hacks everything together we need for the demo.
class DemoTemplateCache() extends TemplateCache {

  private val cacheDir = (
     Option(SnapProperties.SNAP_TEMPLATE_CACHE) map (new java.io.File(_)) getOrElse 
     sys.error("Could not instatiate template cache!  Does this user have a home directory?")
  )

  // First we copy our templates from the snap.home (if we have them there).
  copyTemplatesIfNeeded()

  override val metadata: Set[TemplateMetadata] = 
    Set(
      // TODO - Put hardcoded template metadata here!
    )

  private val index: Map[String, TemplateMetadata] = (metadata map (m => m.id -> m)).toMap
  override def template(id: String): Option[Template] =
    index get id map { metadata =>
      // TODO - Find all files associated with a template...
      val templateDir = new java.io.File(cacheDir, id)
      val fileMappings = for {
        file <- IO allfiles cacheDir
        relative <- IO.relativize(file, cacheDir)
        if !relative.isEmpty
      } yield file -> relative
      Template(metadata, fileMappings) 
    }

  override def search(query: String): Iterable[TemplateMetadata] =
     for {
       m <- metadata
       // Hack so we can search this stuff.
       searchstring = s"""${m.name} ${m.tags mkString " "} ${m.description} ${m.version}"""
       // TODO - better search
       if searchstring contains query
     } yield m


  private def copyTemplatesIfNeeded() {
    // Ensure template cache exists.
    IO.createDirectory(cacheDir)
    // TODO - use SBT IO library when it's on scala 2.10
    import snap.properties.SnapProperties
    for(templateRepo <- Option(SnapProperties.SNAP_TEMPLATE_LOCAL_REPO) map (new java.io.File(_)) filter (_.isDirectory)) {
      // Now loop over all the files in this repo and copy them into the local cache.
      for {
        file <- IO allfiles templateRepo
        relative <- IO.relativize(file, templateRepo)
        if !relative.isEmpty
        to = new java.io.File(cacheDir, relative)
        if !to.exists
      } IO.copyFile(file, to)
    }
  }
}
