package snap

import akka.actor._
import com.typesafe.sbtchild.SbtChildProcessMaker
import java.util.concurrent.atomic.AtomicInteger
import snap.properties.SnapProperties
import scala.util.control.NonFatal

class App(val config: AppConfig, val system: ActorSystem, val sbtMaker: SbtChildProcessMaker) {
  val actor = system.actorOf(Props(new AppActor(config.location, sbtMaker)), name = App.nextName)

  def blueprintUUID: Option[String] =
    try {
      val props = new java.util.Properties
      val input = new java.io.FileInputStream(new java.io.File(config.location, "project/build.properties"))
      try props load input
      finally input.close()
      Option(props.getProperty(SnapProperties.BLUEPRINT_UUID_PROPERTY_NAME))
    } catch {
      case NonFatal(e) => None
    }

  def close(): Unit = {
    system.stop(actor)
  }
}

object App {
  private val nameSerial = new AtomicInteger(1)
  private[snap] def nextName = "app-" + nameSerial.getAndIncrement
}
