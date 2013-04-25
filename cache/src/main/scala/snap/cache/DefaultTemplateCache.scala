package snap
package cache

import java.io.File
import builder.properties.BuilderProperties
import scala.concurrent.Future
import akka.actor.{ ActorRefFactory, Props }
import akka.pattern.ask
import akka.util.Timeout

class DefaultTemplateCache(
  actorFactory: ActorRefFactory,
  provider: IndexDbProvider,
  location: File,
  remote: RemoteTemplateRepository)(
    implicit timeout: Timeout) extends TemplateCache {

  val handler = actorFactory.actorOf(Props(new TemplateCacheActor(provider, location, remote)), "template-cache")
  import actorFactory.dispatcher
  import TemplateCacheActor._

  def template(id: String): Future[Option[Template]] =
    (handler ? GetTemplate(id)).mapTo[TemplateResult].map(_.template)
  def tutorial(id: String): Future[Option[Tutorial]] =
    (handler ? GetTutorial(id)).mapTo[TutorialResult].map(_.tutorial)
  def search(query: String): Future[Iterable[TemplateMetadata]] =
    (handler ? SearchTemplates(query)).mapTo[TemplateQueryResult].map(_.templates)
  def metadata: Future[Iterable[TemplateMetadata]] =
    (handler ? ListTemplates).mapTo[TemplateQueryResult].map(_.templates)
}
object DefaultTemplateCache {
  /** Creates a default template cache for us. */
  def apply(actorFactory: ActorRefFactory,
    remote: RemoteTemplateRepository = defaultRemoteRepo,
    location: File = defaultCacheDir)(
      implicit timeout: Timeout): TemplateCache = {
    ZipInstallHelper.copyLocalCacheIfNeeded(location)
    val indexProvider = LuceneIndexProvider
    // TODO - Copy cache if needed?
    new DefaultTemplateCache(actorFactory, indexProvider, location, remote)
  }

  /** The default remote repository configured via builder properties. */
  def defaultRemoteRepo: RemoteTemplateRepository = {
    // TODO - Implement me!
    new RemoteTemplateRepository {
      def resolveTemplateTo(templateId: String, localDir: File): File = localDir
    }
  }
  /** The default cache directory, configured by builder properties. */
  def defaultCacheDir: File = (
    Option(BuilderProperties.BUILDER_TEMPLATE_CACHE) map (new File(_)) getOrElse
    sys.error("Could not instatiate template cache!  Does this user have a home directory?"))
}