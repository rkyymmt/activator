package com.typesafe.sbtchild

import scala.sys.process.Process
import java.io.File
import akka.actor._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import akka.util.ByteString

sealed trait SbtChildRequest

// these are automatically done during a protocol.Request (subscribe on request,
// unsubscribe on response).
case class SubscribeOutput(ref: ActorRef) extends SbtChildRequest
case class UnsubscribeOutput(ref: ActorRef) extends SbtChildRequest

// You are supposed to send this thing requests from protocol.Request,
// to get back protocol.Reply. It also handles subscribe/unsubscribe from
// output events
class SbtChildActor(workingDir: File, sbtChildMaker: SbtChildProcessMaker) extends EventSourceActor with ActorLogging {

  private val serverSocket = ipc.openServerSocket()
  private val port = serverSocket.getLocalPort()

  private val outDecoder = new ByteStringDecoder
  private val errDecoder = new ByteStringDecoder

  private var protocolStarted = false
  private var protocolStartedAndStopped = false
  // it appears that ActorRef.isTerminated is not guaranteed
  // to be true when we get the Terminated event, which means
  // we have to track these by hand.
  private var serverTerminated = false
  private var processTerminated = false
  private var preStartBuffer = Vector.empty[(protocol.Request, ActorRef)]

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  // TODO the "textMode=true" here shouldn't be needed but scala 2.9.2 seems to not
  // realize that it has a default value? maybe some local quirk on my system?
  private val process = context.actorOf(Props(new ProcessActor(sbtChildMaker.arguments(port),
    workingDir, textMode = true)), "sbt-process")

  private val server = context.actorOf(Props(new ServerActor(serverSocket, self)), "sbt-server")

  watch(server)
  watch(process)

  server ! SubscribeServer(self)
  server ! StartServer
  process ! SubscribeProcess(self)
  process ! StartProcess

  private def considerSuicide(): Unit = {
    if (processTerminated) {
      if (serverTerminated) {
        log.debug("both server actor and process actor terminated, sending suicide pill")
        self ! PoisonPill
      } else if (protocolStartedAndStopped) {
        log.debug("stopped message received from socket to child and child process is dead, killing socket")
        server ! PoisonPill
      }
    } else if (protocolStartedAndStopped) {
      log.debug("stopped message received from socket but process still alive, killing process")
      process ! PoisonPill
    }
  }

  override def onTerminated(ref: ActorRef): Unit = {
    super.onTerminated(ref)
    if (ref == process) {
      processTerminated = true
      // the socket will never connect, if it hasn't.
      // we don't want accept() to perma-block
      log.debug("closing server socket because process exited")
      if (!serverSocket.isClosed())
        serverSocket.close()
      considerSuicide()
    } else if (ref == server) {
      serverTerminated = true
      considerSuicide()
    } else {
      // probably death of a subscriber
    }
  }

  private def forwardRequest(requestor: ActorRef, req: protocol.Request): Unit = {
    if (protocolStarted) {
      // checking isTerminated here is a race, but when the race fails the sender
      // should still time out. We're just trying to short-circuit the timeout if
      // we know it will time out.
      if (serverTerminated || processTerminated || protocolStartedAndStopped) {
        log.debug("Got request {} on already-shut-down server", req)
        requestor ! protocol.ErrorResponse("ServerActor has already shut down")
      } else {
        // otherwise forward the request
        server.tell(req, requestor)
      }
    } else {
      log.debug("storing request for when server gets a connection {}", req)
      preStartBuffer = preStartBuffer :+ (req, sender)
    }
  }

  override def receive = {
    case Terminated(ref) =>
      onTerminated(ref)

    case req: SbtChildRequest => req match {
      case SubscribeOutput(ref) => subscribe(ref)
      case UnsubscribeOutput(ref) => unsubscribe(ref)
    }

    // request for the server actor
    case req: protocol.Request =>
      // auto-subscribe to stdout/stderr; ServerActor is then responsible
      // for sending an Unsubscribe back to us when it sends the Response.
      // Kind of hacky. If ServerActor never starts up, we will stop ourselves
      // and so we don't need to unsubscribe our listeners. We want to subscribe
      // right away, not just when the server starts, because we want sbt's startup
      // messages.
      if (req.sendEvents)
        subscribe(sender)

      // now send the request on
      forwardRequest(sender, req)

    // message from server actor other than a response
    case event: protocol.Event =>
      event match {
        case protocol.Started =>
          protocolStarted = true
          preStartBuffer foreach { m =>
            // We want the requestor to know the boundary between sbt
            // startup and it connecting to us, so it can probabilistically
            // ignore startup messages for example.
            // We do NOT send this event if the request arrives after sbt
            // has already started up, only if the request's lifetime
            // includes an sbt startup.
            if (isSubscribed(m._2))
              m._2.forward(event)
            forwardRequest(m._2, m._1)
          }
          preStartBuffer = Vector.empty
        case protocol.Stopped =>
          log.debug("server actor says it's all done, killing it")
          protocolStartedAndStopped = true
          server ! PoisonPill
        case e: protocol.LogEvent =>
          // this is a log event outside of the context of a particular request
          // (if they go with a request they just go to the requestor)
          emitEvent(e)
        case e: protocol.TestEvent =>
          throw new RuntimeException("Not expecting a TestEvent here: " + e)
        case protocol.MysteryMessage(something) =>
          // let it crash
          throw new RuntimeException("Received unexpected item on socket from sbt child: " + something)
      }

    // event from process actor
    case event: ProcessEvent =>

      def handleOutput(label: String, decoder: ByteStringDecoder, entryMaker: String => protocol.LogEntry, bytes: ByteString): Unit = {
        decoder.feed(bytes)
        val s = decoder.read.mkString
        if (s.length > 0) {
          s.split("\n") foreach { line =>
            log.debug("sbt {}: {}", label, line)
            emitEvent(protocol.LogEvent(entryMaker(line)))
          }
        }
      }

      event match {
        case ProcessStopped(status) =>
          // we don't really need this event since ProcessActor self-suicides
          // and we get Terminated
          log.debug("sbt process stopped, status: {}", status)
        case ProcessStdOut(bytes) =>
          handleOutput("out", outDecoder, protocol.LogStdOut.apply, bytes)
        case ProcessStdErr(bytes) =>
          handleOutput("err", errDecoder, protocol.LogStdErr.apply, bytes)
      }
  }

  override def postStop() = {
    log.debug("postStop")

    preStartBuffer foreach { m =>
      log.debug("On destroy, sending queued request that never made it to the socket {}", m._1)
      m._2 ! protocol.ErrorResponse("sbt process never got in touch, so unable to handle request " + m._1)
    }
    preStartBuffer = Vector.empty

    if (!serverSocket.isClosed())
      serverSocket.close()
  }
}

object SbtChild {
  def apply(factory: ActorRefFactory, workingDir: File, sbtChildMaker: SbtChildProcessMaker): ActorRef =
    factory.actorOf(Props(new SbtChildActor(workingDir, sbtChildMaker)),
      "sbt-child-" + SbtChild.nextSerial.getAndIncrement())

  private val nextSerial = new AtomicInteger(1)
}
