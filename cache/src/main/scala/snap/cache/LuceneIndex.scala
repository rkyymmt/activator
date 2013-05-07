package snap
package cache

import java.io.File
import org.apache.lucene
import lucene.store.Directory
import lucene.index._
import lucene.util.Version.LUCENE_42
import lucene.analysis.standard.StandardAnalyzer
import lucene.document._
import lucene.search.IndexSearcher
import lucene.search.TermQuery
import lucene.queryparser.classic.QueryParser
import lucene.queryparser.classic.MultiFieldQueryParser

object LuceneIndexProvider extends IndexDbProvider {
  def open(localDirOrFile: File): IndexDb = {
    val directory = lucene.store.FSDirectory.open(localDirOrFile)
    new LuceneIndex(directory)
  }
  def write(localDirOrFile: File): IndexWriter = {
    val directory = lucene.store.FSDirectory.open(localDirOrFile)
    new LuceneIndexWriter(directory)
  }
}

// We always assume this is creating a new index....
class LuceneIndexWriter(directory: Directory) extends IndexWriter {
  import LuceneIndex._
  private val writeConfig = {
    val tmp = new IndexWriterConfig(LUCENE_42, analyzer)
    tmp.setOpenMode(lucene.index.IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
    tmp
  }
  private val writer = new lucene.index.IndexWriter(directory, writeConfig)

  def insert(template: IndexStoredTemplateMetadata): Unit = {
    writer addDocument metadataToDocument(template)
  }
  def close(): Unit = {
    writer.close()
  }
}

object LuceneIndex {
  val FIELD_ID = "id"
  val FIELD_NAME = "name"
  val FIELD_TITLE = "title"
  val FIELD_DESC = "description"
  val FIELD_TS = "timestamp"
  val FIELD_TAGS = "tags"
  val FIELD_FEATURED = "featured"
  val FIELD_USAGE_COUNT = "usageCount"

  val FIELD_TRUE_VALUE = "true"
  val FIELD_FALSE_VALUE = "false"
  val FIELD_NONE_VALUE = "None"

  val LUCENE_VERSION = LUCENE_42

  val analyzer = new StandardAnalyzer(LUCENE_VERSION)

  def documentToMetadata(doc: Document): IndexStoredTemplateMetadata = {
    val id = doc get FIELD_ID
    val name = doc get FIELD_NAME
    val title = doc get FIELD_TITLE
    // If we get a failure pulling this as a long, then we have a bad index...
    // We need to figure out how to throw an error and what to do about it...
    // For now, let's just throw any old error and deal on the Actor-side of the fence.
    val ts = (doc get FIELD_TS).toLong
    val desc = doc get FIELD_DESC
    val tags = (doc get FIELD_TAGS) split ","
    val usageCount = (doc get FIELD_USAGE_COUNT) match {
      case FIELD_NONE_VALUE => None
      case LongString(num) => Some(num)
      case _ => None // TODO - just issue a real error here!
    }
    val featured = (doc get FIELD_FEATURED) == FIELD_TRUE_VALUE

    //val
    IndexStoredTemplateMetadata(
      id = id,
      userConfig = UserDefinedTemplateMetadata(
        name = name,
        title = title,
        description = desc,
        tags = tags),
      timeStamp = ts,
      featured = featured,
      usageCount = usageCount)
  }

  def metadataToDocument(metadata: IndexStoredTemplateMetadata): Document = {
    val doc = new Document()
    doc.add(new StringField(FIELD_ID, metadata.id, Field.Store.YES))
    doc.add(new TextField(FIELD_NAME, metadata.name, Field.Store.YES))
    doc.add(new TextField(FIELD_TITLE, metadata.title, Field.Store.YES))
    doc.add(new LongField(FIELD_TS, metadata.timeStamp, Field.Store.YES))
    doc.add(new TextField(FIELD_DESC, metadata.description, Field.Store.YES))
    doc.add(new TextField(FIELD_TAGS, metadata.tags.mkString(","), Field.Store.YES))
    doc.add(new StringField(FIELD_FEATURED, featuredToString(metadata.featured), Field.Store.YES))
    doc.add(new StringField(FIELD_USAGE_COUNT, usageToString(metadata.usageCount), Field.Store.YES))
    doc
  }

  private def featuredToString(featured: Boolean): String =
    if (featured) FIELD_TRUE_VALUE else FIELD_FALSE_VALUE
  private def usageToString(usage: Option[Long]): String =
    usage match {
      case (Some(num)) => num.toString
      case None => FIELD_NONE_VALUE
    }
}
class LuceneIndex(dir: Directory) extends IndexDb {
  import LuceneIndex._
  val reader = DirectoryReader.open(dir)
  val searcher = new IndexSearcher(reader)
  // TODO - Figure out which fields we care about...
  val parser = new MultiFieldQueryParser(LUCENE_VERSION,
    Array(FIELD_TITLE, FIELD_DESC, FIELD_TAGS),
    analyzer);

  def template(id: String): Option[IndexStoredTemplateMetadata] =
    executeQuery(new TermQuery(new Term(FIELD_ID, id)), 1).headOption

  def search(query: String, max: Int): Iterable[IndexStoredTemplateMetadata] =
    executeQuery(parser parse query, max)

  def metadata: Iterable[IndexStoredTemplateMetadata] =
    executeQuery(new lucene.search.MatchAllDocsQuery, reader.maxDoc)

  // Helper which actually runs queries on the local index.
  private def executeQuery(query: lucene.search.Query, max: Long): Iterable[IndexStoredTemplateMetadata] = {
    val results = searcher.search(query, reader.maxDoc)
    results.scoreDocs map { doc =>
      val document = reader.document(doc.doc)
      documentToMetadata(document)
    }
  }
  def close(): Unit = {
    dir.close()
  }
}

object LongString {
  def unapply(x: String) =
    util.Try(x.toLong).toOption
}
