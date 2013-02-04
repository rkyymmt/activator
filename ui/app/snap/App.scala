package snap

import akka.actor._
import com.typesafe.sbtchild.SbtChildProcessMaker
import java.util.concurrent.atomic.AtomicInteger
import snap.properties.SnapProperties
import scala.util.control.NonFatal

class App(val config: AppConfig, val system: ActorSystem, val sbtMaker: SbtChildProcessMaker) {
  val actor = system.actorOf(Props(new AppActor(config.location, sbtMaker)), name = App.nextName)

  // TODO - this method is dangerous, as it hits the file system.
  // Figure out when it should initialize/run.
  val blueprintID: Option[String] =
    try {
      val props = snap.cache.IO loadProperties (new java.io.File(config.location, "project/build.properties"))
      Option(props.getProperty(SnapProperties.BLUEPRINT_UUID_PROPERTY_NAME, null))
    } catch {
      case e: java.io.IOException => None // TODO - Log?
    }

  def close(): Unit = {
    system.stop(actor)
  }
}

object App {
  private val nameSerial = new AtomicInteger(1)
  private[snap] def nextName = "app-" + nameSerial.getAndIncrement
}
