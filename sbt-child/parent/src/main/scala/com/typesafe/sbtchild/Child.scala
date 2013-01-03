package com.typesafe.sbtchild

import scala.sys.process.Process
import java.io.File
import akka.actor._
import java.util.concurrent.atomic.AtomicInteger

// You are supposed to send this thing requests from protocol.Request,
// to get back protocol.Reply
class SbtChildActor(workingDir: File, sbtChildMaker: SbtChildProcessMaker) extends Actor {

  private val serverSocket = ipc.openServerSocket()
  private val port = serverSocket.getLocalPort()

  private val outDecoder = new ByteStringDecoder
  private val errDecoder = new ByteStringDecoder

  private var started = false
  private var preStartBuffer = Vector.empty[MakeServerRequest]

  // TODO the "textMode=true" here shouldn't be needed but scala 2.9.2 seems to not
  // realize that it has a default value? maybe some local quirk on my system?
  private val process = context.actorOf(Props(new ProcessActor(sbtChildMaker.arguments(port),
    workingDir, textMode = true)), "sbt-process")

  private val server = context.actorOf(Props(new ServerActor(serverSocket)), "sbt-server")

  server ! SubscribeServer(self)
  server ! StartServer
  process ! SubscribeProcess(self)
  process ! StartProcess

  override def receive = {
    // request for the server actor
    case req: protocol.Request =>
      val message = MakeServerRequest(sender, req)
      if (started) {
        server ! message
      } else {
        preStartBuffer = preStartBuffer :+ message
      }

    // message from server actor other than a response
    case event: protocol.Event => event match {
      case protocol.Started =>
        started = true
        preStartBuffer foreach { m => server ! m }
        preStartBuffer = Vector.empty
      case protocol.Stopped =>
        // FIXME do what? we sort of need to wait for both of
        // our child actors to stop.
        System.err.println("ipc stopped")
      case protocol.MysteryMessage(something) =>
        // let it crash
        throw new Exception("Received unexpected item on socket from sbt child: " + something)
    }

    // event from process actor
    case event: ProcessEvent => event match {
      case ProcessStopped(status) =>
        // FIXME do something better, wait for both child actors to stop, then close down?
        System.err.println("sbt stopped, status: " + status)
      case ProcessStdOut(bytes) => {
        outDecoder.feed(bytes)
        // FIXME do something better
        val s = outDecoder.read.mkString
        if (s.length > 0)
          System.err.println("sbt out: " + s.trim())
      }
      case ProcessStdErr(bytes) => {
        errDecoder.feed(bytes)
        // FIXME do something better
        val s = errDecoder.read.mkString
        if (s.length > 0)
          System.err.println("sbt err: " + s.trim())
      }
    }
  }

  override def postStop() = {
    if (!serverSocket.isClosed())
      serverSocket.close()
  }
}

object SbtChild {
  def apply(system: ActorSystem, workingDir: File, sbtChildMaker: SbtChildProcessMaker): ActorRef = system.actorOf(Props(new SbtChildActor(workingDir, sbtChildMaker)),
    "sbt-child-" + SbtChild.nextSerial.getAndIncrement())

  private val nextSerial = new AtomicInteger(1)
}
