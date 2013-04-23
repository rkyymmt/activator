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
  val testData: Seq[TemplateMetadata] =
    Seq(TemplateMetadata("ID",
      "url-friendly-name",
      "A human readable title.",
      1L,
      "A very long description; DELETE TABLE TEMPLATES; with SQL injection.",
      Seq("Tag 1", "Tag 2", "tag3")),
      TemplateMetadata("ID-2",
        "url-friendly-name-2",
        "A not so Human readable title.",
        2L,
        "A long description for fun, this one is the fun document.",
        Seq("Tag 1", "Tag 4")))

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

class SqlLiteIndexDbTest extends IndexDbTest {
  def provider = SqlLiteIndex
}

class LuceneIndexDbTest extends IndexDbTest {
  def provider = LuceneIndexProvider
}
