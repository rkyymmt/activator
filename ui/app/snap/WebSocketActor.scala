package snap

import akka.actor._
import akka.pattern._
import scala.concurrent.{ Channel => _, _ }
import scala.concurrent.duration._
import akka.util._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.libs.iteratee._
import scala.collection.immutable.Queue
import play.api.mvc.WebSocket.FrameFormatter

private case object Ack

case object GetWebSocket

private case object CloseWebSocket

// This is a bunch of glue to convert Iteratee/Enumerator into an actor.
// There's probably a better approach, oh well.
abstract class WebSocketActor[MessageType](implicit frameFormatter: FrameFormatter[MessageType], mf: Manifest[MessageType]) extends Actor with ActorLogging {
  import WebSocketActor.timeout
  private implicit def ec: ExecutionContext = context.system.dispatcher

  protected sealed trait WebSocketMessage
  protected case class Incoming[In](message: In) extends WebSocketMessage

  private sealed trait InternalWebSocketMessage
  private case object IncomingComplete extends InternalWebSocketMessage
  private case object Ready extends InternalWebSocketMessage
  private case object InitialReadyTimeout extends InternalWebSocketMessage
  private case object TimeoutAfterHalfCompleted extends InternalWebSocketMessage

  // This is a consumer which is pushed to by the websocket handler
  private class ActorIteratee[In](val actor: ActorRef) extends Iteratee[In, Unit] {
    // we are an iteratee that always _continues_ by providing the function
    // handleNextInput, which in turn computes the next iteratee based on
    // some input fed to us from the websocket. The next iteratee will
    // be another ActorIteratee, or a Done or an Error.
    override def fold[B](folder: Step[In, Unit] => Future[B]): Future[B] = folder(Step.Cont(handleNextInput))

    private def handleNextInput(i: Input[In]): Iteratee[In, Unit] = {
      i match {
        case Input.Empty ⇒
          log.debug("consumer iteratee (incoming websocket messages) is empty")
          this
        case Input.EOF ⇒ {
          log.debug("consumer iteratee (incoming websocket messages) EOF")
          actor ! IncomingComplete
          Done((), Input.Empty)
        }
        case Input.El(x) ⇒ {
          if (actor.isTerminated) {
            log.debug("Sending error to the incoming websocket, can't consume since actor is terminated {}", i)
            Error("web socket consumer actor has been terminated", i)
          } else {
            actor.ask(Incoming[In](x))(WebSocketActor.timeout).onFailure({
              case e: Exception ⇒
                log.warning("Failed to consume incoming websocket message due to terminated actor")
            })
            this
          }
        }
      }
    }
  }

  // this is called from a non-actor thread
  private def newConsumer(): Iteratee[MessageType, Unit] = new ActorIteratee[MessageType](self)

  private var incomingCompleted = false
  private var outgoingCompleted = false
  private var triggeredFullyCompleted = false
  private var createdSocket = false
  private var ready = false

  // don't restart children
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private val producer = context.actorOf(Props(new ProducerProxy[MessageType]), name = "producer")

  context.watch(producer)

  override def preStart(): Unit = {
    log.debug("starting")
    context.system.scheduler.scheduleOnce(WebSocketActor.timeout.duration, self, InitialReadyTimeout)
  }

  private def checkFullyCompleted() {
    // it's possible that ready has never been true when we get here
    if (incomingCompleted && outgoingCompleted) {
      if (!triggeredFullyCompleted) {
        log.debug("Both incoming and outgoing websocket channels done, killing websocket actor")
        triggeredFullyCompleted = true
        self ! PoisonPill
      }
    }

    if (incomingCompleted || outgoingCompleted) {
      context.system.scheduler.scheduleOnce(WebSocketActor.timeout.duration, self, TimeoutAfterHalfCompleted)
    }
  }

  private def internalReceive: Receive = {
    case Terminated(child) ⇒
      if (child == producer) {
        log.debug("In websocket actor, got Terminated for producer actor")
        outgoingCompleted = true
        checkFullyCompleted()
      } else {
        log.warning("In websocket actor, got Terminated for unexpected actor: " + child)
      }
    case internal: InternalWebSocketMessage ⇒ internal match {
      case IncomingComplete ⇒
        log.debug("In websocket actor, got IncomingComplete signaling consumer actor is done")
        incomingCompleted = true
        checkFullyCompleted()
        log.debug("poisoning producer to close our side of the socket")
        producer ! PoisonPill
      case InitialReadyTimeout ⇒
        if (!ready) {
          log.warning("websocket actor not ready within its timeout, poisoning")
          self ! PoisonPill
        }
      case Ready ⇒
        ready = true
      case TimeoutAfterHalfCompleted ⇒
        log.warning("websocket actor had incoming completed=" + incomingCompleted +
          " and outgoing completed=" + outgoingCompleted +
          " and timed out before the other one completed; force-terminating")
        incomingCompleted = true
        outgoingCompleted = true
        checkFullyCompleted()
    }
    case Incoming(message) ⇒
      onMessage(message.asInstanceOf[MessageType])
    case GetWebSocket ⇒
      if (createdSocket) {
        log.warning("second connection attempt will fail")
        throw new Exception("Tried to attach a second web socket to the same WebSocketActor")
      } else {
        log.info("Firing up web socket")
        val actor = self
        val futureStreams = producer.ask(GetProducer)
          .mapTo[GotProducer[MessageType]]
          .map({
            case GotProducer(enumerator) ⇒
              log.debug("got producer enumerator")
              val consumer = newConsumer()
              actor ! Ready
              (consumer, enumerator)
          })
        createdSocket = true

        futureStreams onFailure {
          case e: Throwable =>
            log.warning("Failed to create producer and consumer, {}: {}", e.getClass.getSimpleName, e.getMessage)
        }

        futureStreams pipeTo sender
      }
    case CloseWebSocket =>
      log.debug("got CloseWebSocket poisoning the producer")
      producer ! PoisonPill
  }

  final override def receive = internalReceive orElse subReceive

  protected def subReceive: Receive = Map.empty

  protected def onMessage(message: MessageType): Unit = {

  }

  protected final def produce(message: MessageType): Unit = {
    if (producer.isTerminated) {
      // this isn't reliable, it's just nicer to fail early instead of timing out
      log.warning("producer actor is dead, sending isn't going to work")
    } else {
      producer.ask(OutgoingMessage(message))(WebSocketActor.timeout).mapTo[Ack.type].onFailure {
        case e: Exception ⇒
          log.debug("Producer actor failed to send Outgoing, {}: {}", e.getClass.getSimpleName, e.getMessage)
          log.debug("Killing failed producer")
          // this is supposed to start a chain reaction where we get Terminated
          // on the producer and then kill ourselves as well
          producer ! PoisonPill
      }
    }
  }
}

object WebSocketActor {
  implicit val timeout = Timeout(20.seconds)
}

private sealed trait ProducerProxyMessage
private case class OutgoingReady[Out](channel: Concurrent.Channel[Out]) extends ProducerProxyMessage
private case object OutgoingComplete extends ProducerProxyMessage
private case class OutgoingError[Out](s: String, input: Input[Out]) extends ProducerProxyMessage
private case class OutgoingMessage[Out](message: Out) extends ProducerProxyMessage
private case object GetProducer extends ProducerProxyMessage

private sealed trait ProducerProxyReply
private case class GotProducer[Out](enumerator: Enumerator[Out]) extends ProducerProxyReply

private class ProducerProxy[Out] extends Actor with ActorLogging {
  private implicit def ec: ExecutionContext = context.system.dispatcher

  private case object InitialReadyTimeout

  // create a producer that accepts outgoing websocket messages
  // and sends us status updates on the producer channel
  protected lazy val enumerator = Concurrent.unicast[Out](
    onStart = { channel ⇒
      log.debug("unicast onStart: sending channel to websocket producer")
      self ! OutgoingReady[Out](channel)
    },
    onComplete = { () ⇒
      log.debug("unicast onComplete: completing websocket producer")
      self ! OutgoingComplete
    },
    onError = { (s, input) ⇒
      log.debug("unicast onError: websocket producer {}", s)
      self ! OutgoingError(s, input)
    })

  var channelOption: Option[Concurrent.Channel[Out]] = None
  var buffer: Queue[Out] = Queue.empty

  private def push(message: Out): Unit = {
    require(channelOption.isDefined)
    for (channel <- channelOption) {
      log.debug("pushing message to channel {}", message)
      try {
        channel.push(message)
        log.debug("message pushed with no exception")
      } catch {
        case other: Exception ⇒
          log.warning("Exception {} sending to socket, suiciding: {}", other.getClass.getSimpleName, other.getMessage)
          self ! PoisonPill
      }
    }
  }

  private def produce(message: Out): Unit = {
    if (channelOption.isDefined) {
      push(message)
    } else {
      log.debug("Buffering message {}", message)
      buffer = buffer.enqueue(message)
    }
  }

  private def flushBuffer(): Unit = {
    require(channelOption.isDefined)

    if (buffer.isEmpty)
      log.debug("No messages in buffer to flush")

    while (buffer.nonEmpty) {
      val (m, remaining) = buffer.dequeue
      log.debug("Flushing buffered message {}", m)
      push(m)
      buffer = remaining
    }
  }

  override def receive = {
    case InitialReadyTimeout ⇒
      if (!channelOption.isDefined) {
        log.warning("ProducerProxy not ready within initial timeout, poisoning")
        self ! PoisonPill
      }
    case ppMessage: ProducerProxyMessage ⇒ ppMessage match {
      case OutgoingMessage(message) ⇒
        log.debug("producer got outgoing: {}", message)
        produce(message.asInstanceOf[Out])
        log.debug("producer sending Ack")
        sender ! Ack
      case OutgoingReady(channel) ⇒
        log.debug("ProducerProxy ready to go, got channel {}", channel)
        require(channelOption.isEmpty)
        channelOption = Some(channel.asInstanceOf[Concurrent.Channel[Out]])
        flushBuffer()
      case OutgoingComplete ⇒
        log.debug("ProducerProxy got complete, closing down)")
        self ! PoisonPill
      case OutgoingError(what, input) ⇒
        log.debug("ProducerProxy got error, closing down: {}", what)
        self ! PoisonPill
      case GetProducer ⇒
        log.debug("ProducerProxy returning its enumerator: {}", enumerator)
        sender ! GotProducer(enumerator)
    }
  }

  override def preStart(): Unit = {
    log.debug("starting")
    context.system.scheduler.scheduleOnce(WebSocketActor.timeout.duration, self, InitialReadyTimeout)
  }

  override def postStop(): Unit = {
    log.debug("stopping")
    channelOption.foreach { channel =>
      try channel.eofAndEnd() catch {
        case e: Exception ⇒
          log.warning("Problem closing websocket outgoing producer: {}: {}", e.getClass.getSimpleName, e.getMessage)
      }
    }
  }
}
