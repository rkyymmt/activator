package activator

import activator.properties.ActivatorProperties
import activator.properties.ActivatorProperties.SCRIPT_NAME
import activator.cache._
import activator.cache.Actions.cloneTemplate
import akka.actor.ActorRefFactory
import java.io.File
import activator.cache.RemoteTemplateRepository
import com.typesafe.config.ConfigFactory

// This helper constructs the template cache in the default CLI/UI location.
object UICacheHelper {
  def makeDefaultCache(actorFactory: ActorRefFactory)(implicit timeout: akka.util.Timeout): TemplateCache = {
    // TODO - Config or ActiavtorProperties?
    val config = ConfigFactory.load()
    val remote = RemoteTemplateRepository(config)

    DefaultTemplateCache(
      actorFactory = actorFactory,
      location = new File(ActivatorProperties.ACTIVATOR_TEMPLATE_CACHE),
      remote = remote,
      seedRepository = Option(ActivatorProperties.ACTIVATOR_TEMPLATE_LOCAL_REPO) map (new File(_)) filter (_.isDirectory))
  }
}
