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

  def insert(template: TemplateMetadata): Unit = {
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
  val LUCENE_VERSION = LUCENE_42
  val analyzer = new StandardAnalyzer(LUCENE_VERSION)

  def documentToMetadata(doc: Document): TemplateMetadata = {
    val id = doc get FIELD_ID
    val name = doc get FIELD_NAME
    val title = doc get FIELD_TITLE
    // If we get a failure pulling this as a long, then we have a bad index...
    // We need to figure out how to throw an error and what to do about it...
    // For now, let's just throw any old error and deal on the Actor-side of the fence.
    val ts = (doc get FIELD_TS).toLong
    val desc = doc get FIELD_DESC
    val tags = (doc get FIELD_TAGS) split ","

    //val
    TemplateMetadata(
      id = id,
      name = name,
      title = title,
      timeStamp = ts,
      description = desc,
      tags = tags)
  }

  def metadataToDocument(metadata: TemplateMetadata): Document = {
    val doc = new Document()
    doc.add(new StringField(FIELD_ID, metadata.id, Field.Store.YES))
    doc.add(new TextField(FIELD_NAME, metadata.name, Field.Store.YES))
    doc.add(new TextField(FIELD_TITLE, metadata.title, Field.Store.YES))
    doc.add(new LongField(FIELD_TS, metadata.timeStamp, Field.Store.YES))
    doc.add(new TextField(FIELD_DESC, metadata.description, Field.Store.YES))
    doc.add(new TextField(FIELD_TAGS, metadata.tags.mkString(","), Field.Store.YES))
    doc
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

  def template(id: String): Option[TemplateMetadata] =
    executeQuery(new TermQuery(new Term(FIELD_ID, id)), 1).headOption

  def search(query: String, max: Int): Iterable[TemplateMetadata] =
    executeQuery(parser parse query, max)

  def metadata: Iterable[TemplateMetadata] =
    executeQuery(new lucene.search.MatchAllDocsQuery, reader.maxDoc)

  // Helper which actually runs queries on the local index.
  private def executeQuery(query: lucene.search.Query, max: Long): Iterable[TemplateMetadata] = {
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
