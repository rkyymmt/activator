package snap

import com.typesafe.sbtchild._
import akka.actor._
import java.io.File

sealed trait AppRequest

case object GetSbtPool extends AppRequest

sealed trait AppReply

case class SbtPoolReply(ref: ActorRef) extends AppReply

class AppActor(val location: File, val sbtMaker: SbtChildProcessMaker) extends Actor {

  val childFactory = new DefaultSbtChildFactory(location, sbtMaker)
  val sbts = context.actorOf(Props(new ChildPool(childFactory)))

  override def receive = {
    case req: AppRequest => req match {
      case GetSbtPool =>
        sender ! SbtPoolReply(sbts)
    }
  }
}
