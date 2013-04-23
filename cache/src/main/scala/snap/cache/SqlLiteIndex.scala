package snap
package cache

import scala.slick._
import scala.slick.session.{
  Session,
  Database
}
import scala.slick.driver.SQLiteDriver
import scala.slick.driver.SQLiteDriver.Table
import scala.slick.driver.SQLiteDriver.Implicit._
import java.util.Properties

/** Implements our index db using SQLite and Slick sessions. */
class SqlLiteIndex(session: Session) extends IndexDb with IndexWriter {
  import SqlLiteIndex._
  import slick.jdbc.StaticQuery.interpolation
  // TODO - Sanity check the database...
  def insert(template: TemplateMetadata): Unit = {
    import template._
    Templates.insert(
      id,
      name,
      title,
      timeStamp,
      description,
      tags mkString ",")(session)
  }

  private def convertFromSlick(row: (String, String, String, Long, String, String)): TemplateMetadata =
    TemplateMetadata(
      id = row._1,
      name = row._2,
      title = row._3,
      timeStamp = row._4,
      description = row._5,
      tags = row._6 split ",")

  def template(id: String): Option[TemplateMetadata] = {
    val q = (for {
      t <- Templates
      if t.id === id
    } yield t).take(1)
    q.run(session).headOption map convertFromSlick
  }
  def search(query: String, max: Int): Iterable[TemplateMetadata] = {
    // In the future we should care about the search query...
    val likeQuery = s"%$query%"
    // TODO - better query here.
    val q = for {
      t <- Templates
      if (t.description like likeQuery) ||
        (t.title like likeQuery) ||
        (t.tags like likeQuery) ||
        (t.name like likeQuery)
    } yield t
    q.take(max).run(session) map convertFromSlick
  }

  def metadata: Iterable[TemplateMetadata] = {
    val q = for {
      t <- Templates
    } yield t
    q.run(session) map convertFromSlick
  }

  def close(): Unit =
    session.close()
}
object SqlLiteIndex extends IndexDbProvider {

  def open(localDirOrFile: java.io.File): IndexDb = {
    new SqlLiteIndex(newSession(localDirOrFile))
  }

  def write(localDirOrFile: java.io.File): IndexWriter = {
    new SqlLiteIndex(newSession(localDirOrFile))
  }

  private def newSession(dir: java.io.File) = {
    val url = getFileUrl(dir)
    val db = openDb(url, "", "", null)
    val session = db.createSession
    setupDb(session)
    session
  }

  val driver = new org.sqlite.JDBC

  private def openDb(url: String, user: String, password: String, prop: Properties): session.Database =
    session.Database.forDriver(driver, url, user, password, prop)

  // TODO - does this work on windows?  
  private def getFileUrl(file: java.io.File): String =
    s"jdbc:sqlite:${file.getCanonicalPath}"

  object Templates extends Table[(String, String, String, Long, String, String)]("TEMPLATES") {
    def id = column[String]("ID", O.PrimaryKey)
    def name = column[String]("NAME")
    def title = column[String]("TITLE")
    def timeStamp = column[Long]("TIMESTAMP")
    def description = column[String]("DESC")
    def tags = column[String]("TAGS")
    def * = id ~ name ~ title ~ timeStamp ~ description ~ tags

    def idx = index("idx_id", id, unique = true)
    def idxDesc = index("idx_search", description, unique = false)
  }

  import slick.jdbc.StaticQuery.interpolation
  private def setupDb(session: Session): Unit = {
    try createTable(session)
    catch {
      case ex if ex.getMessage contains "already exists" => ()
    }
    // TODO - Other sanity checks?
  }

  private def createTable(session: Session): Unit =
    Templates.ddl.create(session)
}

