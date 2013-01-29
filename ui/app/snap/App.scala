package snap

import akka.actor._
import com.typesafe.sbtchild.SbtChildProcessMaker
import java.util.concurrent.atomic.AtomicInteger

class App(val config: ProjectConfig, val system: ActorSystem, val sbtMaker: SbtChildProcessMaker) {
  val actor = system.actorOf(Props(new AppActor(config.location, sbtMaker)), name = App.nextName)

  def close(): Unit = {
    system.stop(actor)
  }
}

object App {
  private val nameSerial = new AtomicInteger(1)
  private[snap] def nextName = "app-" + nameSerial.getAndIncrement
}
