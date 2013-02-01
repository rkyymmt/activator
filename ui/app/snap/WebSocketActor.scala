package snap

// for the "10 seconds" duration sugar
import language.postfixOps

import akka.actor._
import akka.pattern._
import scala.concurrent._
import scala.concurrent.duration._
import akka.util._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Promise
import scala.collection.immutable.Queue
import play.api.mvc.WebSocket.FrameFormatter

private case object Ack

private case object GetWebSocket

// This is a bunch of glue to convert Iteratee/Enumerator into an actor.
// There's probably a better approach, oh well.
abstract class WebSocketActor[MessageType](implicit frameFormatter: FrameFormatter[MessageType], mf: Manifest[MessageType]) extends Actor with ActorLogging {
  private implicit def timeout = WebSocketActor.timeout
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
            actor.ask(Incoming[In](x), 5 seconds).onFailure({
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

  // when the WebSocketActor is stopped, this is also stopped, and
  // then the enumerator is closed.
  private var producerOption: Option[ActorRef] = None

  private var incomingCompleted = false
  private var outgoingCompleted = false
  private var triggeredFullyCompleted = false
  private var createdSocket = false
  private var ready = false

  override def preStart(): Unit = {
    producerOption = Some(context.actorOf(Props(new ProducerProxy[MessageType])))
    producerOption.foreach(context.watch(_))
    context.system.scheduler.scheduleOnce(11 seconds, self, InitialReadyTimeout)
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
      context.system.scheduler.scheduleOnce(12 seconds, self, TimeoutAfterHalfCompleted)
    }
  }

  private def internalReceive: Receive = {
    case Terminated(child) ⇒
      if (Some(child) == producerOption) {
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
          " and timed out before the other one completed")
        incomingCompleted = true
        outgoingCompleted = true
        checkFullyCompleted()
    }
    case Incoming(message) ⇒
      onMessage(message.asInstanceOf[MessageType])
    case GetWebSocket ⇒
      if (createdSocket) {
        throw new Exception("Tried to attach a second web socket to the same WebSocketActor")
      } else {
        val actor = self
        val futureStreams = producerOption.getOrElse(throw new Exception("no producer created")).ask(GetProducer)
          .mapTo[Enumerator[MessageType]]
          .map({ enumerator ⇒
            val consumer = newConsumer()
            actor ! Ready
            (consumer, enumerator)
          })
        createdSocket = true
        futureStreams pipeTo sender
      }
  }

  final override def receive = internalReceive orElse subReceive

  protected def subReceive: Receive = Map.empty

  protected def onMessage(message: MessageType): Unit = {

  }

  protected def produce(message: MessageType): Unit = {
    require(producerOption.isDefined)

    producerOption.foreach({ producer ⇒
      producer.ask(Outgoing(message), 5 seconds).onFailure({
        case e: Exception ⇒
          log.debug("Producer actor failed to send Outgoing, {}: {}", e.getClass.getSimpleName, e.getMessage)
          // this is supposed to start a chain reaction where we get Terminated
          // on the producer and then kill ourselves as well
          producer ! PoisonPill
      })
    })
  }
}

object WebSocketActor {
  implicit val timeout = Timeout(20 seconds)

  def createHandler[M](webSocketActor: ActorRef)(implicit frameFormatter: FrameFormatter[M], mf: Manifest[M], executor: ExecutionContext): Future[WebSocket[M]] = {
    webSocketActor.ask(GetWebSocket)
      .mapTo[(Iteratee[M, _], Enumerator[M])]
      .map({ streams ⇒
        WebSocket.using({ header ⇒ streams })
      })
  }
}

private sealed trait ProducerProxyMessage
private case object OutgoingReady extends ProducerProxyMessage
private case object OutgoingComplete extends ProducerProxyMessage
private case class OutgoingError[Out](s: String, input: Input[Out]) extends ProducerProxyMessage
private case class Outgoing[Out](message: Out) extends ProducerProxyMessage
private case object GetProducer extends ProducerProxyMessage

private class ProducerProxy[Out] extends Actor with ActorLogging {
  private implicit def ec: ExecutionContext = context.system.dispatcher

  private case object InitialReadyTimeout

  // create a producer that accepts outgoing websocket messages
  // and sends us status updates on the producer channel

  protected lazy val (stream, channel) = Concurrent.broadcast[Out]

  protected lazy val enumerator = Enumerator.imperative[Out](
    onStart = { () ⇒
      log.debug("starting websocket producer (outgoing socket)")
      self ! OutgoingReady
    },
    onComplete = { () ⇒
      log.debug("completing websocket producer")
      self ! OutgoingComplete
    },
    onError = { (s, input) ⇒
      log.debug("error on websocket producer")
      self ! OutgoingError(s, input)
    })

  var ready = false
  var buffer: Queue[Out] = Queue.empty

  private def push(message: Out): Unit = {
    require(ready)
    try {
      if (enumerator.push(message.asInstanceOf[Out]))
        log.debug("pushed message over socket")
      else
        throw new Exception("Tried to push over bad outgoing channel (not yet open?)")
    } catch {
      case other: Exception ⇒
        log.warning("Exception {} sending to socket, suiciding: {}", other.getClass.getSimpleName, other.getMessage)
        self ! PoisonPill
    }
  }

  private def produce(message: Out): Unit = {
    if (ready) {
      push(message)
    } else {
      log.debug("Buffering message {}", message)
      buffer = buffer.enqueue(message)
    }
  }

  private def flushBuffer(): Unit = {
    require(ready)

    while (buffer.nonEmpty) {
      val (e, remaining) = buffer.dequeue
      log.debug("Flushing buffered message {}", e)
      push(e)
      buffer = remaining
    }
  }

  override def receive = {
    case InitialReadyTimeout ⇒
      if (!ready) {
        log.warning("ProducerProxy not ready within initial timeout, poisoning")
        self ! PoisonPill
      }
    case ppMessage: ProducerProxyMessage ⇒ ppMessage match {
      case Outgoing(message) ⇒
        produce(message.asInstanceOf[Out])
        sender ! Ack
      case OutgoingReady ⇒
        log.debug("ProducerProxy ready to go, flushing buffer")
        ready = true
        flushBuffer()
      case OutgoingComplete ⇒
        log.debug("ProducerProxy got complete, closing down)")
        self ! PoisonPill
      case OutgoingError(what, input) ⇒
        log.debug("ProducerProxy got error, closing down: {}", what)
        self ! PoisonPill
      case GetProducer ⇒
        log.debug("ProducerProxy returning its enumerator: {}", enumerator)
        sender ! enumerator
    }
  }

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(10 seconds, self, InitialReadyTimeout)
  }

  override def postStop(): Unit = {
    try {
      enumerator.close()
    } catch {
      case e: Exception ⇒
        log.warning("Problem closing websocket outgoing producer: {}: {}", e.getClass.getSimpleName, e.getMessage)
    }
    log.debug("Closed websocket producer actor")
  }
}
