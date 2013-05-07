package snap
package cache

import org.junit.Assert._
import org.junit._

trait IndexDbTest {
  def provider: IndexDbProvider
  def index = _db

  @Test
  def testFindById(): Unit = {
    for (metadata <- testData) {
      val found = index.template(metadata.id)
      assertEquals("Unable to find metadata in index.", Some(metadata), found)
    }
  }

  def testFindInList(): Unit = {
    val metadata = testData.head
    val lists = index.metadata
    val contained = lists.exists(_ == metadata)
    assertTrue("Unable to find metadata in index.", contained)
  }

  @Test
  def testFindByQuery(): Unit = {
    val metadata = testData.head
    val found = index.search("human")
    val contained = found.exists(_ == metadata)
    assertTrue(s"Unable to find metadata in index.  Result = ${found mkString "\n"}", contained)
  }

  // Hackery to ensure the database is opened closed.
  private var _db: IndexDb = null
  val testData: Seq[IndexStoredTemplateMetadata] =
    Seq(IndexStoredTemplateMetadata(
      id = "ID",
      userConfig = AuthorDefinedTemplateMetadata(
        "url-friendly-name",
        "A human readable title.",
        "A very long description; DELETE TABLE TEMPLATES; with SQL injection.",
        Seq("Tag 1", "Tag 2", "tag3")),
      timeStamp = 1L,
      featured = true,
      usageCount = None),
      IndexStoredTemplateMetadata(
        id = "ID-2",
        userConfig = AuthorDefinedTemplateMetadata(
          "url-friendly-name-2",
          "A human readable title.  AGAIN!",
          "A very long description\n WITH mutliple lines and stuff.  This is not featured.\n",
          Seq("Tag 1", "Tag 2", "tag3")),
        timeStamp = 1L,
        featured = false,
        usageCount = None))

  @Before
  def preStart(): Unit = {
    val tmp = java.io.File.createTempFile("indexdb", "test")
    tmp.delete()

    val writer = provider write tmp
    try testData foreach writer.insert
    finally writer.close()

    _db = provider.open(tmp)
  }
  @After
  def postStop(): Unit = {
    if (_db != null)
      _db.close()
    _db = null
  }
}

class LuceneIndexDbTest extends IndexDbTest {
  def provider = LuceneIndexProvider
}
