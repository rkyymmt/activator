package snap

import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import akka.pattern._
import play.api.libs.json.JsValue

// This guy stores the Akka we use for eventing.
object Akka {
  // TODO - use my thread context
  val system = withContextCl(akka.actor.ActorSystem())

  val events = system.actorOf(akka.actor.Props[EventActor]())

  // it's basically a bug anytime a timeout needs to be this
  // long, because in practice it won't expire before the user
  // just kills the app.
  val longTimeoutThatIsAProblem = Timeout(1200.seconds)

  // retry an async op with a short timeout between tries
  def retryOverMilliseconds[T](ms: Int)(thing: => Future[T]): Future[T] = {
    val interval = 300 // ms
    retryNTimes(Math.max(ms / interval, 1), interval)(thing)
  }

  private def retryNTimes[T](n: Int, interval: Int)(thing: => Future[T]): Future[T] = {
    import system.dispatcher
    val f = thing
    if (n > 0) {
      f recoverWith {
        case e: Exception =>
          play.api.Logger.debug(s"Will retry in ${interval} milliseconds (failure was ${e.getClass.getName}: ${e.getMessage})")
          val p = Promise[T]()
          system.scheduler.scheduleOnce(interval.milliseconds) {
            play.api.Logger.debug("Retrying now...")
            p.completeWith(retryNTimes(n - 1, interval)(thing))
          }
          p.future
      }
    } else {
      f
    }
  }

  private def withContextCl[A](f: => A): A = {
    val cl = Thread.currentThread.getContextClassLoader
    Thread.currentThread.setContextClassLoader(this.getClass.getClassLoader)
    try f
    finally {
      Thread.currentThread.setContextClassLoader(cl)
    }
  }
}

case object DeathReportTimedOut

// This handles ONE message from ONE sender, informing the sender
// if the receiving delegate dies
class DeathReportingProxy(val delegate: ActorRef)(implicit val timeout: akka.util.Timeout) extends Actor with ActorLogging {

  import context.dispatcher

  var originalSender: Option[ActorRef] = None

  var terminated = delegate.isTerminated

  context.watch(delegate)
  context.system.scheduler.scheduleOnce(timeout.duration, self, DeathReportTimedOut)

  // called from any thread
  private def completeOurMission(self: ActorRef, sender: ActorRef, message: Any): Unit = {
    sender ! message
    self ! PoisonPill
  }

  // called from any thread
  private def informOfDeath(self: ActorRef, sender: ActorRef): Unit = {
    completeOurMission(self, sender, Status.Failure(new Exception(s"actor $delegate has terminated")))
  }

  override def receive = {

    case DeathReportTimedOut =>
      // this happens if we never got a message and the delegate never
      // died.
      self ! PoisonPill

    case Terminated(ref) if ref == delegate =>
      terminated = true
      // this makes us self-destruct if we send to the sender
      originalSender.foreach(informOfDeath(self, _))

    case message =>
      if (terminated) {
        informOfDeath(self, context.sender)
      } else {
        val f = (delegate ? message)
        val proxy = self
        val sender = context.sender
        originalSender = Some(sender)
        f onSuccess {
          case v =>
            completeOurMission(proxy, sender, v)
        }
        f onFailure {
          case e: Throwable =>
            completeOurMission(proxy, sender, Status.Failure(e))
        }
      }
  }
}

object DeathReportingProxy {
  private val count = new java.util.concurrent.atomic.AtomicInteger(1)

  def ask(factory: ActorRefFactory, recipient: ActorRef, message: Any)(implicit timeout: akka.util.Timeout): Future[Any] = {
    val proxy = factory.actorOf(Props(new DeathReportingProxy(recipient)), name = s"deathproxy-${count.getAndIncrement()}")
    akka.pattern.ask(proxy, message)
  }
}

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
