package snap.cache

import snap.IO
import activator.properties.ActivatorProperties
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
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

/**
 * A mapping of the files included in this tutorial.
 *
 * The map is relative-file-name to actual File.
 */
case class Tutorial(id: String, files: Map[String, java.io.File])
/**
 * All information about a template.
 * @files is a sequence of actual file -> relative location name.
 */
case class Template(metadata: TemplateMetadata,
  files: Seq[(java.io.File, String)]) // TODO - What do we need for help?

/**
 * This interface represents the template cache within SNAP.  it's your mechanisms to find things and
 *  create stuff.
 */
trait TemplateCache {
  /** Find a template within the cache. */
  def template(id: String): Future[Option[Template]]
  /** Find the tutorial for a given template. */
  // TODO - Different method, or against Template?
  def tutorial(id: String): Future[Option[Tutorial]]
  /** Search for a template within the cache. */
  def search(query: String): Future[Iterable[TemplateMetadata]]
  /** Returns all metadata we have for templates. */
  def metadata: Future[Iterable[TemplateMetadata]]

}
