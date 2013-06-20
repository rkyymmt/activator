package snap

import akka.actor._
import com.typesafe.sbtrc._
import java.util.concurrent.atomic.AtomicInteger
import activator.properties.ActivatorProperties
import scala.util.control.NonFatal
import java.net.URLEncoder

class App(val config: AppConfig, val system: ActorSystem, val sbtProcessLauncher: SbtProcessLauncher) {
  val actor = system.actorOf(Props(new AppActor(config, sbtProcessLauncher)), name = "app-" + URLEncoder.encode(config.id, "UTF-8"))

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
