package snap.cache


import snap.properties.SnapProperties

// TODO - This may need more work.
// TODO - We probably are on the limit of fields for a useful case-class....
case class TemplateMetadata(id: String,
                             name: String,
                             version: String,
                             description: String,
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

  import java.io.File
  private def defaultTemplateFiles: Seq[(File, String)] = {
    def fileFor(loc: String, name: String): Option[(File, String)] = Option(loc) map (new File(_)) filter (_.exists) map (_ -> name)
    val batFile = fileFor(SnapProperties.SNAP_LAUNCHER_BAT, "snap.bat")
    val jarFile = fileFor(SnapProperties.SNAP_LAUNCHER_JAR, s"snap-launch-${SnapProperties.APP_VERSION}.jar")
    val bashFile = fileFor(SnapProperties.SNAP_LAUNCHER_BASH, "snap")
    Seq(batFile, jarFile, bashFile).flatten
  }

  override val metadata: Set[TemplateMetadata] = 
    Set(
      // TODO - Put more hardcoded template metadata for the demo here!
      TemplateMetadata(
        id = "a5227c77d39109b6550a47758c2f9a1341e06524",
        name = "Getting Started with Play in Java",
        version = "1.0",
        description = """|Get started with a Java web application.  This Blueprint will walk you through 
                         |the basics of building a Java web application using the Typesafe technologies.  
                         |You will first learn the basics of the Play Framework.""".stripMargin,
        tags = Seq("play", "java", "starter")
      )
    )

  private val index: Map[String, TemplateMetadata] = (metadata map (m => m.id -> m)).toMap
  override def template(id: String): Option[Template] =
    index get id map { metadata =>
      // TODO - Find all files associated with a template...
      val templateDir = new java.io.File(cacheDir, id)
      val fileMappings = for {
        file <- IO allfiles templateDir
        relative <- IO.relativize(templateDir, file)
        if !relative.isEmpty
      } yield file -> relative
      Template(metadata, fileMappings ++ defaultTemplateFiles) 
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
        relative <- IO.relativize(templateRepo, file)
        if !relative.isEmpty
        to = new java.io.File(cacheDir, relative)
        if !to.exists
      } if(file.isDirectory) IO.createDirectory(to)
        else                 IO.copyFile(file, to)
    }
  }
}
