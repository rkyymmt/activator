package snap
package cache

import java.io.File
import akka.actor.Actor

/**
 * This actor provides an implementation of the tutorial cache
 * that allows us to handle failures via actor-restart and threading
 * via single-access to the cache.
 *
 * Although, if we use lucene, we could have multi-threaded access, best
 * not to assume technology for now.
 * 
 * TODO - Add a manager in front of this actor that knows how to update the lucene index and reboot this guy.
 */
class TemplateCacheActor(provider: IndexDbProvider, location: File, remote: RemoteTemplateRepository) extends Actor {
  import TemplateCacheActor._

  def receive: Receive = {
    // TODO - Make sure we send failures to sender as well, so futures
    // complete immediately.
    case GetTemplate(id: String) => sender ! TemplateResult(getTemplate(id))
    case GetTutorial(id: String) => sender ! TutorialResult(getTutorial(id))
    case SearchTemplates(query, max) => sender ! TemplateQueryResult(searchTemplates(query, max))
    case ListTemplates => sender ! TemplateQueryResult(listTemplates)
  }

  def listTemplates = index.metadata

  def searchTemplates(query: String, max: Int): Iterable[TemplateMetadata] =
    index.search(query, max)

  def getTutorial(id: String): Option[Tutorial] = {
    val tutorialDir = new java.io.File(getTemplateDirAndEnsureLocal(id), "tutorial")
    if (tutorialDir.exists) {
      val fileMappings = for {
        file <- IO allfiles tutorialDir
        if !file.isDirectory
        relative <- IO.relativize(tutorialDir, file)
        if !relative.isEmpty
      } yield relative -> file
      Some(Tutorial(id, fileMappings.toMap))
    } else None
  }

  def getTemplate(id: String): Option[Template] = {
    index.template(id) match {
      case Some(metadata) =>
        val localDir = getTemplateDirAndEnsureLocal(id)
        val fileMappings = for {
          file <- IO allfiles localDir
          relative <- IO.relativize(localDir, file)
          if !relative.isEmpty
          if !(relative startsWith "tutorial")
        } yield file -> relative
        Some(Template(metadata, fileMappings))
      case _ => None
    }
  }

  private def getTemplateDirAndEnsureLocal(id: String): File = {
    // TODO - return a file that is friendly for having tons of stuff in it,
    // i.e. maybe we take the first N of the id and use that as a directory first.
    remote.resolveTemplateTo(id, new java.io.File(location, id))
  }

  var index: IndexDb = null

  override def preStart(): Unit = {
    // Our index is underneath the cache location...
    index = provider.open(new File(location, "index.db"))
  }
  override def postStop(): Unit = {
    if (index != null) {
      index.close()
    }
  }
}

object TemplateCacheActor {
  case class GetTemplate(id: String)
  case class GetTutorial(id: String)
  case class SearchTemplates(query: String, max: Int = 0)
  case object ListTemplates

  case class TemplateResult(template: Option[Template])
  case class TemplateQueryResult(templates: Iterable[TemplateMetadata])
  case class TutorialResult(tutorial: Option[Tutorial])
}