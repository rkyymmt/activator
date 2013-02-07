package com.typesafe.sbtchild

import scala.sys.process.Process
import java.io.File
import akka.actor._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

// You are supposed to send this thing requests from protocol.Request,
// to get back protocol.Reply
class SbtChildActor(workingDir: File, sbtChildMaker: SbtChildProcessMaker) extends Actor with ActorLogging {

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

  private val server = context.actorOf(Props(new ServerActor(serverSocket)), "sbt-server")

  context.watch(server)
  context.watch(process)

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

  override def receive = {
    case Terminated(ref) =>
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
        log.warning("Likely bug, got unknown death notification {}", ref)
      }

    // request for the server actor
    case req: protocol.Request =>
      if (protocolStarted) {
        // checking isTerminated here is a race, but when the race fails the sender
        // should still time out. We're just trying to short-circuit the timeout if
        // we know it will time out.
        if (serverTerminated || processTerminated || protocolStartedAndStopped) {
          log.debug("Got request {} on already-shut-down server", req)
          sender ! protocol.ErrorResponse("ServerActor has already shut down")
        }
        server.forward(req)
      } else {
        log.debug("storing request for when server gets a connection {}", req)
        preStartBuffer = preStartBuffer :+ (req, sender)
      }

    // message from server actor other than a response
    case event: protocol.Event => event match {
      case protocol.Started =>
        protocolStarted = true
        preStartBuffer foreach { m =>
          log.debug("Sending queued request to new server {}", m._1)
          server.tell(m._1, m._2)
        }
        preStartBuffer = Vector.empty
      case protocol.Stopped =>
        log.debug("server actor says it's all done, killing it")
        protocolStartedAndStopped = true
        server ! PoisonPill
      case protocol.MysteryMessage(something) =>
        // let it crash
        throw new RuntimeException("Received unexpected item on socket from sbt child: " + something)
    }

    // event from process actor
    case event: ProcessEvent => event match {
      case ProcessStopped(status) =>
        // we don't really need this event since ProcessActor self-suicides
        // and we get Terminated
        log.debug("sbt process stopped, status: {}", status)
      case ProcessStdOut(bytes) => {
        outDecoder.feed(bytes)
        // FIXME do something better
        val s = outDecoder.read.mkString
        if (s.length > 0)
          log.debug("sbt out: {}", s.trim())
      }
      case ProcessStdErr(bytes) => {
        errDecoder.feed(bytes)
        // FIXME do something better
        val s = errDecoder.read.mkString
        if (s.length > 0)
          log.debug("sbt err: {}", s.trim())
      }
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
