package snap

import akka.actor._
import com.typesafe.sbtrc._
import java.util.concurrent.atomic.AtomicInteger
import activator.properties.ActivatorProperties
import scala.util.control.NonFatal
import java.net.URLEncoder

class App(val config: AppConfig, val system: ActorSystem, val sbtProcessLauncher: SbtProcessLauncher) {
  val appInstance = App.nextInstanceId.getAndIncrement()
  override def toString = s"App(${config.id}@$appInstance})"
  val actorName = "app-" + URLEncoder.encode(config.id, "UTF-8") + "-" + appInstance

  val actor = system.actorOf(Props(new AppActor(config, sbtProcessLauncher)),
    name = actorName)

  // TODO - this method is dangerous, as it hits the file system.
  // Figure out when it should initialize/run.
  val templateID: Option[String] =
    try {
      val props = new java.util.Properties
      sbt.IO.load(props, new java.io.File(config.location, "project/build.properties"))
      Option(props.getProperty(ActivatorProperties.TEMPLATE_UUID_PROPERTY_NAME, null))
    } catch {
      case e: java.io.IOException => None // TODO - Log?
    }

}

object App {
  val nextInstanceId = new AtomicInteger(1)
}
