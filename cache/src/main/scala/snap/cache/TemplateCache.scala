package snap.cache

import snap.IO
import activator.properties.ActivatorProperties

// TODO - This whole thing should use an abstraction file files, like "Source" or some such.

// TODO - This may need more work.
// TODO - We probably are on the limit of fields for a useful case-class....
case class TemplateMetadata(
  id: String,
  name: String, // Web/Console friendly-name  Must be unique.
  title: String, // Human readable name for website
  timeStamp: Long, // A timestamp of when this guy was generated
  description: String,
  tags: Seq[String]) {
  // TODO - update equality/hashcode to be based on ID
}

/** A mapping of the files included in this tutorial. */
case class Tutorial(id: String, files: Map[String, java.io.File])
case class Template(metadata: TemplateMetadata,
  files: Seq[(java.io.File, String)]) // TODO - What do we need for help?

/**
 * This interface represents the template cache within SNAP.  it's your mechanisms to find things and
 *  create stuff.
 */
trait TemplateCache {
  /** Find a template within the cache. */
  def template(id: String): Option[Template]
  /** Find the tutorial for a given template. */
  // TODO - Different method, or against Template?
  def tutorial(id: String): Option[Tutorial]
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
    Option(ActivatorProperties.ACTIVATOR_TEMPLATE_CACHE) map (new java.io.File(_)) getOrElse
    sys.error("Could not instatiate template cache!  Does this user have a home directory?"))

  // First we copy our templates from the snap.home (if we have them there).
  copyTemplatesIfNeeded()

  import java.io.File
  private def defaultTemplateFiles: Seq[(File, String)] = {
    def fileFor(loc: String, name: String): Option[(File, String)] = Option(loc) map (new File(_)) filter (_.exists) map (_ -> name)
    val batFile = fileFor(ActivatorProperties.ACTIVATOR_LAUNCHER_BAT, "activator.bat")
    val jarFile = fileFor(ActivatorProperties.ACTIVATOR_LAUNCHER_JAR, s"activator-launch-${ActivatorProperties.APP_VERSION}.jar")
    val bashFile = fileFor(ActivatorProperties.ACTIVATOR_LAUNCHER_BASH, "activator")
    Seq(batFile, jarFile, bashFile).flatten
  }

  override val metadata: Set[TemplateMetadata] =
    Set(
      // TODO - Put more hardcoded template metadata for the demo here!
      TemplateMetadata(
        id = "f9a3508cefd6408c6b993b5d90b328a72c1779d8",
        name = "Reactive Stocks",
        version = "1.0",
        description = """The Reactive Stocks application uses Java, Scala, Play Framework, and Akka to illustrate a reactive app.  The tutorial in this example will teach you the reactive basics including Reactive Composition and Reactive Push.""",
        tags = Seq("java", "scala", "play framework", "akka", "reactive")),
      TemplateMetadata(
        id = "a5227c77d39109b6550a47758c2f9a1341e06524",
        name = "hello-scala",
        title = "Hello Scala!",
        timeStamp = 1,
        description = """Scala is a general purpose programming language designed to express common programming patterns in a concise, elegant, and type-safe way.  This very simple Scala application will get you started building and testing standalone Scala apps.  This app uses Scala 2.10 and ScalaTest.""",
        tags = Seq("Basics", "scala", "starter")),
      TemplateMetadata(
        id = "39836f5aa646b3a37abb80e8a2c335ebf6830cac",
        name = "hello-akka",
        title = "Hello Akka!",
        timeStamp = 1,
        description = """Akka is a toolkit and runtime for building highly concurrent, distributed, and fault tolerant event-driven apps.  This simple application will get you started building Actor based systems in Java and Scala.  This app uses Akka 2.1, Java 6, Scala 2.10, JUnit, and ScalaTest.""",
        tags = Seq("Basics", "akka", "java", "scala", "starter")),
      TemplateMetadata(
        id = "c63e1fe7748dcebdc0fc0243685e5ae6d1ec4072",
        name = "hello-play",
        title = "Hello Play Framework!",
        timeStamp = 1,
        description = """Play Framework is the High Velocity Web Framework for Java and Scala.  Play is based on a lightweight, stateless, web-friendly architecture.  Built on Akka, Play provides predictable and minimal resource comsumption (CPU, memory, threads) for highly-scalable applications.  This app will teach you how to start building Play 2.1 apps with Java and Scala.""",
        tags = Seq("Basics", "play", "java", "scala", "starter")))

  private val index: Map[String, TemplateMetadata] = (metadata map (m => m.id -> m)).toMap
  override def template(id: String): Option[Template] =
    index get id map { metadata =>
      // TODO - Find all files associated with a template...
      val templateDir = new java.io.File(cacheDir, id)
      val fileMappings = for {
        file <- IO allfiles templateDir
        relative <- IO.relativize(templateDir, file)
        if !relative.isEmpty
        if !(relative startsWith "tutorial")
      } yield file -> relative
      Template(metadata, fileMappings ++ defaultTemplateFiles)
    }

  override def tutorial(id: String): Option[Tutorial] =
    index get id map { metadata =>
      // TODO - Find all files associated with a template...
      val templateDir = new java.io.File(cacheDir, id + "/tutorial")
      val fileMappings = for {
        file <- IO allfiles templateDir
        if !file.isDirectory
        relative <- IO.relativize(templateDir, file)
        if !relative.isEmpty
      } yield relative -> file
      Tutorial(id, fileMappings.toMap)
    }

  override def search(query: String): Iterable[TemplateMetadata] =
    for {
      m <- metadata
      // Hack so we can search this stuff.
      searchstring = s"""${m.title} ${m.name} ${m.tags mkString " "} ${m.description}"""
      // TODO - better search
      if searchstring contains query
    } yield m

  private def copyTemplatesIfNeeded() {
    // Ensure template cache exists.
    IO.createDirectory(cacheDir)
    // TODO - use SBT IO library when it's on scala 2.10
    for (templateRepo <- Option(ActivatorProperties.ACTIVATOR_TEMPLATE_LOCAL_REPO) map (new java.io.File(_)) filter (_.isDirectory)) {
      // Now loop over all the files in this repo and copy them into the local cache.
      for {
        file <- IO allfiles templateRepo
        relative <- IO.relativize(templateRepo, file)
        if !relative.isEmpty
        to = new java.io.File(cacheDir, relative)
        if !to.exists
      } if (file.isDirectory) IO.createDirectory(to)
      else IO.copyFile(file, to)
    }
  }
}
