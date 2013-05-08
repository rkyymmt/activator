package snap
package cache

import org.junit.Assert._
import org.junit._
import java.io.File
import akka.actor._
import concurrent.Await
import concurrent.duration._

class DefaultTemplateCacheTest {

  var cacheDir: File = null
  var system: ActorSystem = null
  var cache: TemplateCache = null
  implicit val timeout = akka.util.Timeout(1000L)

  @Before
  def setup() {
    cacheDir = snap.IO.createTemporaryDirectory
    // TODO - Create an cache...
    makeTestCache(cacheDir)
    system = ActorSystem()
    // TODO - stub out remote repo
    cache = DefaultTemplateCache(actorFactory = system, location = cacheDir)
  }

  @Test
  def resolveTemplate(): Unit = {
    val template =
      Await.result(cache.template("ID-1"), Duration(1, MINUTES))
    assertTrue(template.isDefined)
    assertEquals(template.get.metadata, template1)
    val hasBuildSbt = template.get.files exists {
      case (file, name) => name == "build.sbt"
    }
    assertTrue("Failed to find template files!", hasBuildSbt)
  }

  @Test
  def resolveTutorial(): Unit = {
    val tutorial =
      Await.result(cache.tutorial("ID-1"), Duration(1, MINUTES))
    assertTrue(tutorial.isDefined)
    val hasIndexHtml = tutorial.get.files exists {
      case (name, file) => name == "index.html"
    }
    assertTrue("Failed to find tutorial files!", hasIndexHtml)
  }
  @Test
  def getAllMetadata(): Unit = {
    val metadata =
      Await.result(cache.metadata, Duration(1, MINUTES))
    val hasMetadata = metadata exists { _ == template1 }
    assertTrue("Failed to find metadata!", hasMetadata)
    val hasRemote = metadata exists { _ == nonLocalTemplate }
    assertTrue("Failed to find non-local template!", hasRemote)
  }

  @Test
  def getFeaturedMetadata(): Unit = {
    val metadata =
      Await.result(cache.featured, Duration(1, MINUTES))
    val hasMetadata = metadata exists { _ == template1 }
    assertTrue("Failed to find metadata!", hasMetadata)
    assertFalse("Featured metadata has unfeatured template.", metadata.exists(_ == nonLocalTemplate))
  }

  @Test
  def search(): Unit = {
    val metadata =
      Await.result(cache.search("test"), Duration(1, MINUTES))
    val hasMetadata = metadata exists { _ == template1 }
    assertTrue("Failed to find metadata in seaarch!", hasMetadata)
  }

  @Test
  def badSearch(): Unit = {
    val metadata =
      Await.result(cache.search("Ralph"), Duration(1, MINUTES))
    val hasMetadata = metadata exists { _ == template1 }
    assertFalse("Failed to find metadata in seaarch!", hasMetadata)
  }

  @After
  def tearDown() {
    system.shutdown()
    snap.IO delete cacheDir
    cacheDir = null
  }

  val template1 = TemplateMetadata(
    IndexStoredTemplateMetadata(
      id = "ID-1",
      timeStamp = 1L,
      featured = true,
      usageCount = None,
      userConfig = AuthorDefinedTemplateMetadata(
        name = "test-template",
        title = "A Testing Template",
        description = "A template that tests template existance.",
        tags = Seq("test", "template"))),
    locallyCached = true)

  val nonLocalTemplate = TemplateMetadata(
    IndexStoredTemplateMetadata(
      id = "ID-2",
      timeStamp = 1L,
      featured = false,
      usageCount = None,
      userConfig = AuthorDefinedTemplateMetadata(
        name = "test-remote-template",
        title = "A Testing Template that is not dowloaded",
        description = "A template that tests template existentialism.",
        tags = Seq("test", "template"))),
    locallyCached = false)
  def makeTestCache(dir: File): Unit = {
    val writer = LuceneIndexProvider.write(new File(dir, Constants.METADATA_INDEX_FILENAME))
    try {
      writer.insert(template1.persistentConfig)
      writer.insert(nonLocalTemplate.persistentConfig)
    } finally {
      writer.close()
    }
    // Now we create our files:
    val templateDir = new File(dir, "ID-1")
    snap.IO createDirectory templateDir
    snap.IO.write(new File(templateDir, "build.sbt"), """name := "Test" """)
    val tutorialDir = new File(templateDir, Constants.TUTORIAL_DIR)
    snap.IO createDirectory tutorialDir
    snap.IO.write(new File(tutorialDir, "index.html"), "<html></html>")
  }
}
