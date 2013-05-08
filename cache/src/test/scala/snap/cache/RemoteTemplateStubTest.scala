package snap
package cache

import org.junit.Assert._
import org.junit._
import java.io.File
import akka.actor._
import concurrent.Await
import concurrent.duration._

class RemoteTemplateStubTest {

  val FIRST_INDEX_ID = "FIRST INDEX"
  val SECOND_INDEX_ID = "SECOND INDEX"

  var cacheDir: File = null
  var system: ActorSystem = null
  var cache: TemplateCache = null
  implicit val timeout = akka.util.Timeout(60 * 1000L)

  object StubRemoteRepository extends RemoteTemplateRepository {
    def hasNewIndex(currentHash: String): Boolean =
      FIRST_INDEX_ID == currentHash

    // TODO - Actually alter the index and check to see if we have the new one.
    // Preferable with a new template, not in the existing index.
    def resolveIndexTo(indexDirOrFile: File): String = {
      makeIndex(indexDirOrFile)(
        template1,
        nonLocalTemplate,
        newNonLocalTemplate)
      SECOND_INDEX_ID
    }

    def resolveTemplateTo(templateId: String, localDir: File): File = {
      if (nonLocalTemplate.id == templateId || newNonLocalTemplate.id == templateId) {
        // Fake Resolving a remote template
        if (!localDir.exists) snap.IO.createDirectory(localDir)
        snap.IO.write(new File(localDir, "build2.sbt"), """name := "Test2" """)
        val tutorialDir = new File(localDir, Constants.TUTORIAL_DIR)
        snap.IO createDirectory tutorialDir
        snap.IO.write(new File(tutorialDir, "index.html"), "<html></html>")
      }
      localDir
    }
  }

  @Before
  def setup() {
    cacheDir = snap.IO.createTemporaryDirectory
    // TODO - Create an cache...
    makeTestCache(cacheDir)
    system = ActorSystem()
    // TODO - stub out remote repo
    cache = DefaultTemplateCache(
      actorFactory = system,
      location = cacheDir,
      remote = StubRemoteRepository)
  }

  @Test
  def resolveTemplate(): Unit = {
    val template =
      Await.result(cache.template("ID-1"), Duration(3, MINUTES))
    assertTrue(template.isDefined)
    assertEquals(template.get.metadata, template1)
    val hasBuildSbt = template.get.files exists {
      case (file, name) => name == "build.sbt"
    }
    assertTrue("Failed to find template files!", hasBuildSbt)
  }

  @Test
  def resolveRemoteTemplate(): Unit = {
    val template =
      Await.result(cache.template(nonLocalTemplate.id), Duration(3, MINUTES))
    assertTrue(template.isDefined)
    assertEquals(template.get.metadata, resolvedNonLocalTemplate)
    val hasBuildSbt = template.get.files exists {
      case (file, name) => name == "build2.sbt"
    }
    assertTrue("Failed to find template files!", hasBuildSbt)
  }

  @Test
  def resolveNewRemoteTemplate(): Unit = {
    val template =
      Await.result(cache.template(newNonLocalTemplate.id), Duration(3, MINUTES))
    assertTrue(template.isDefined)
    assertEquals(template.get.metadata, resolvedNewNonLocalTemplate)
    val hasBuildSbt = template.get.files exists {
      case (file, name) => name == "build2.sbt"
    }
    assertTrue("Failed to find template files!", hasBuildSbt)
  }

  @Test
  def resolveTutorial(): Unit = {
    val tutorial =
      Await.result(cache.tutorial(template1.id), Duration(3, MINUTES))
    assertTrue(tutorial.isDefined)
    val hasIndexHtml = tutorial.get.files exists {
      case (name, file) => name == "index.html"
    }
    assertTrue("Failed to find tutorial files!", hasIndexHtml)
  }
  @Test
  def getAllMetadata(): Unit = {
    val metadata =
      Await.result(cache.metadata, Duration(3, MINUTES))
    val hasMetadata = metadata exists { _ == template1 }
    assertTrue("Failed to find metadata!", hasMetadata)
    val hasRemote = metadata exists { _ == nonLocalTemplate }
    assertTrue("Failed to find non-local template!", hasRemote)

    val hasNewRemote = metadata exists { _ == newNonLocalTemplate }
    assertTrue("Failed to find new non-local template!", hasNewRemote)
  }

  @Test
  def getFeaturedMetadata(): Unit = {
    val metadata =
      Await.result(cache.featured, Duration(3, MINUTES))
    val hasMetadata = metadata exists { _ == template1 }
    assertTrue("Failed to find metadata!", hasMetadata)
    assertFalse("Featured metadata has unfeatured template.", metadata.exists(_ == nonLocalTemplate))
    val hasNewRemote = metadata exists { _ == newNonLocalTemplate }
    assertTrue("Failed to find new non-local template!", hasNewRemote)
  }

  @Test
  def search(): Unit = {
    val metadata =
      Await.result(cache.search("test"), Duration(3, MINUTES))
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
    // Here we always check to ensure the properties are right....
    val cacheProps = new CacheProperties(new File(cacheDir, Constants.CACHE_PROPS_FILENAME))
    assertEquals("Failed to download new metadata index!", SECOND_INDEX_ID, cacheProps.cacheIndexHash)
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

  val resolvedNonLocalTemplate =
    nonLocalTemplate.copy(locallyCached = true)

  val newNonLocalTemplate = TemplateMetadata(
    IndexStoredTemplateMetadata(
      id = "ID-3",
      timeStamp = 1L,
      featured = true,
      usageCount = None,
      userConfig = AuthorDefinedTemplateMetadata(
        name = "test-updated-template",
        title = "A NEW FEATURED TEMPLATE",
        description = "A template that tests template deism.  MONADS.",
        tags = Seq("test", "template"))),
    locallyCached = false)

  val resolvedNewNonLocalTemplate =
    newNonLocalTemplate.copy(locallyCached = true)

  def makeIndex(dir: File)(templates: TemplateMetadata*): Unit = {
    if (dir.exists) snap.IO.delete(dir)
    val writer = LuceneIndexProvider.write(dir)
    try templates foreach { t => writer insert t.persistentConfig }
    finally writer.close()
  }

  def makeTestCache(dir: File): Unit = {
    makeIndex(new File(dir, Constants.METADATA_INDEX_FILENAME))(
      template1,
      nonLocalTemplate)
    // Now we create our files:
    val templateDir = new File(dir, "ID-1")
    snap.IO createDirectory templateDir
    snap.IO.write(new File(templateDir, "build.sbt"), """name := "Test" """)
    val tutorialDir = new File(templateDir, Constants.TUTORIAL_DIR)
    snap.IO createDirectory tutorialDir
    snap.IO.write(new File(tutorialDir, "index.html"), "<html></html>")
    val cacheProps = new CacheProperties(new File(dir, Constants.CACHE_PROPS_FILENAME))
    cacheProps.cacheIndexHash = FIRST_INDEX_ID
    cacheProps.save()
  }
}
