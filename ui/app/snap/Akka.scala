package snap

import akka.util.Timeout
import scala.concurrent.duration._

// This guy stores the Akka we use for eventing.
object Akka {
  // TODO - use my thread context
  val system = withContextCl(akka.actor.ActorSystem())

  val events = system.actorOf(akka.actor.Props[EventActor]())

  // it's basically a bug anytime a timeout needs to be this
  // long, because in practice it won't expire before the user
  // just kills the app.
  val longTimeoutThatIsAProblem = Timeout(1200.seconds)

  private def withContextCl[A](f: => A): A = {
    val cl = Thread.currentThread.getContextClassLoader
    Thread.currentThread.setContextClassLoader(this.getClass.getClassLoader)
    try f
    finally {
      Thread.currentThread.setContextClassLoader(cl)
    }
  }
}

import akka.actor.{ Actor, ActorRef, Props }

// TODO - Cleanup and thread stuff.
class EventActor extends Actor {
  import akka.actor.{ OneForOneStrategy, SupervisorStrategy }
  import SupervisorStrategy.Stop
  import concurrent.duration.Duration.Zero
  // When one of our children has an error, we just stop the stream for now and assume the client will reconnect and
  // make a new listener.
  override val supervisorStrategy = OneForOneStrategy(0, Zero) {
    case _ => Stop
  }

  def receive: Receive = {
    case "Kill Children" =>
      context.children foreach context.stop
    case newListenerProps: Props =>
      // Make a new listener using the set of props and return the ActorRef.
      // Note: We don't have to keep track of it, because our context monitors our children.
      sender ! (context actorOf newListenerProps)
    case msg: String =>
      println(s"Sending msg to ${context.children.toIndexedSeq.size} children")
      context.children foreach (_ ! msg)
    // TODO - Take in event messages we can adapt into JSON strings...
  }
}
