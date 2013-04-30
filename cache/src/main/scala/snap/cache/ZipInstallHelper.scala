package snap
package cache

import activator.properties.ActivatorProperties
import java.io.File
// This class contains methods that are responsible for pulling
// local caches out of our local zip file.
// We place it in its own object to denote the terribleness of the
// hackery used.
object ZipInstallHelper {
  // This logic is responsible for copying the local template cache (if available)
  // from the distribution exploded-zip directory into the user's local
  // cache.
  def copyLocalCacheIfNeeded(cacheDir: File): Unit = {
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
    // TODO - Remove this hack when we update from remote repo.
    if (!(new File(cacheDir, "index.db").exists)) {
      makeCheaterIndex(cacheDir)
    }
  }

  def makeCheaterIndex(cacheDir: File): Unit = {
    val writer = LuceneIndexProvider.write(new File(cacheDir, "index.db"))
    try {
      cheaterIndex foreach writer.insert
    } finally writer.close()
  }

  private val cheaterIndex = Set(
    // TODO - Put more hardcoded template metadata for the demo here!
    TemplateMetadata(
      id = "f9a3508cefd6408c6b993b5d90b328a72c1779d8",
      name = "reactive-stocks",
      title = "Reactive Stocks",
      timeStamp = 1,
      description = """The Reactive Stocks application uses Java, Scala, Play Framework, and Akka to illustrate a reactive app.  The tutorial in this example will teach you the reactive basics including Reactive Composition and Reactive Push.""",
      tags = Seq("Sample", "java", "scala", "play framework", "akka", "reactive")),
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
}
