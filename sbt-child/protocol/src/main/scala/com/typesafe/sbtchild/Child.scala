package com.typesafe.sbtchild

import scala.sys.process.Process
import java.io.File
import akka.actor._
import java.util.concurrent.atomic.AtomicInteger

// You are supposed to send this thing requests from protocol.Request,
// to get back protocol.Reply
class SbtChildActor(workingDir: File) extends Actor {

  private val serverSocket = ipc.openServerSocket()
  private val port = serverSocket.getLocalPort()

  private val outDecoder = new ByteStringDecoder
  private val errDecoder = new ByteStringDecoder

  private var started = false
  private var preStartBuffer = Vector.empty[MakeServerRequest]

  // FIXME don't hardcode my homedir (we want the launcher to be part of snap)
  private val process = context.actorOf(Props(new ProcessActor(Seq("java",
    "-Dsnap.sbt-child-port=" + port,
    "-Dsbt.boot.directory=/home/hp/.sbt/boot",
    "-Xss1024K", "-Xmx1024M", "-XX:PermSize=512M", "-XX:+CMSClassUnloadingEnabled",
    "-jar",
    "/opt/hp/bin/sbt-launch-0.12.0.jar",
    // command to add our special hook
    "apply com.typesafe.sbt.SetupSbtChild",
    // enter the "get stuff from the socket" loop
    "listen"),
    workingDir)), "sbt-process")

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

    // message from server actor
    case message: protocol.Message => message match {
      case protocol.Started =>
        started = true
        preStartBuffer foreach { m => server ! m }
        preStartBuffer = Vector.empty
      case protocol.Stopped =>
        // FIXME do what?
        System.err.println("ipc stopped")
    }

    // event from process actor
    case event: ProcessEvent => event match {
      case ProcessStopped(status) =>
        // FIXME do something better
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
  def apply(system: ActorSystem, workingDir: File): ActorRef = system.actorOf(Props(new SbtChildActor(workingDir)),
    "sbt-child-" + SbtChild.nextSerial.getAndIncrement())

  private val nextSerial = new AtomicInteger(1)
}
