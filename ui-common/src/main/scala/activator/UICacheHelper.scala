package activator

import activator.properties.ActivatorProperties
import activator.properties.ActivatorProperties.SCRIPT_NAME
import activator.cache._
import activator.cache.Actions.cloneTemplate
import akka.actor.ActorRefFactory
import java.io.File
import activator.cache.RemoteTemplateRepository
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.ActorContext

// This helper constructs the template cache in the default CLI/UI location.
object UICacheHelper {
  def makeDefaultCache(actorFactory: ActorRefFactory)(implicit timeout: akka.util.Timeout): TemplateCache = {
    // TODO - Config or ActiavtorProperties?
    val config = ConfigFactory.load()
    val log = actorFactory match {
      case system: ActorSystem => system.log
      case context: ActorContext => context.system.log
      case whatever => throw new RuntimeException(s"don't know how to get log from $whatever")
    }
    val remote = RemoteTemplateRepository(config, log)
    val localCache = new File(ActivatorProperties.ACTIVATOR_TEMPLATE_CACHE)
    val localSeed = Option(ActivatorProperties.ACTIVATOR_TEMPLATE_LOCAL_REPO) map (new File(_)) filter (_.isDirectory)
    DefaultTemplateCache(
      actorFactory = actorFactory,
      location = localCache,
      remote = remote,
      seedRepository = localSeed)
  }
}
