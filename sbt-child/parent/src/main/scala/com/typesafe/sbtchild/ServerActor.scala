package com.typesafe.sbtchild

import akka.actor._
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.ServerSocket
import java.net.SocketException
import java.io.EOFException

// these are messages to/from the actor; we also just
// forward the Envelope directly from the wire
sealed trait ServerActorRequest
case class SubscribeServer(ref: ActorRef) extends ServerActorRequest
case class UnsubscribeServer(ref: ActorRef) extends ServerActorRequest
// Ask the server to accept connections
case object StartServer extends ServerActorRequest
// sent by the server to itself when it accepts the connection
case class ServerAccepted(server: ipc.Server) extends ServerActorRequest

class ServerActor(serverSocket: ServerSocket, childActor: ActorRef) extends Actor with ActorLogging {
  var subscribers: Set[ActorRef] = Set.empty

  var serverOption: Option[ipc.Server] = None

  val pool = NamedThreadFactory.newPool("ServerActor")

  val selfRef = context.self

  case class Requestor(ref: ActorRef, sendEvents: Boolean)

  var pendingReplies = Map.empty[Long, Requestor]

  var disconnected = false

  override def receive = {
    case req: ServerActorRequest => req match {
      case SubscribeServer(ref) =>
        subscribers = subscribers + ref
        context.watch(ref)
      case UnsubscribeServer(ref) =>
        subscribers = subscribers - ref
        context.unwatch(ref)
      case StartServer =>
        start()
      case ServerAccepted(server) =>
        serverOption = Some(server)
    }

    case req: protocol.Request => {
      if (disconnected) {
        sender ! protocol.ErrorResponse("sbt socket is disconnected")
      } else {
        val server = serverOption.getOrElse(throw new Exception("Made request before server accept"))
        try {
          val requestSerial = server.sendJson(req)
          val pair = (requestSerial -> Requestor(sender, req.sendEvents))
          pendingReplies += pair
          log.debug("  added to pending replies: {}", pair)
        } catch {
          case e: Exception =>
            log.warning("failed to send message to child process", e)
            sender ! protocol.ErrorResponse(e.getMessage)
        }
      }
    }

    case Terminated(ref) =>
      subscribers = subscribers - ref

    case e: protocol.Envelope =>
      if (e.replyTo != 0L) {
        // replies or events that "go with" a certain request
        val requestor = pendingReplies.get(e.replyTo).getOrElse(throw new RuntimeException("Nobody was waiting for " + e))
        e.content match {
          case response: protocol.Response =>
            log.debug("  dispatching pending reply to {}", e.replyTo)
            pendingReplies = pendingReplies - e.replyTo
            childActor ! UnsubscribeOutput(requestor.ref) // auto-unsub the requestor
            requestor.ref ! response
          case event: protocol.Event =>
            log.debug("  got event during request {}, {}, requestor={}", e.replyTo, event, requestor.ref)
            if (requestor.sendEvents)
              requestor.ref ! event
            else
              log.debug("Dropping event which was not requested {}", event)
          case whatever =>
            throw new RuntimeException("not expecting replyTo on: " + whatever)
        }

      } else {
        // events
        log.debug("  dispatching event {} to {} subscribers", e.content, subscribers.size)
        for (s <- subscribers) {
          s ! e.content
        }
        e.content match {
          case protocol.Stopped =>
            log.debug("  marking disconnected due to protocol.Stopped")
            disconnected = true
          case _ =>
        }
      }
  }

  def start(): Unit = {
    pool.execute(new Runnable() {
      override def run = {
        try {
          log.debug("  waiting for connection from child process")
          val server = ipc.accept(serverSocket)
          try {
            if (!serverSocket.isClosed())
              serverSocket.close() // we only want one connection
            selfRef ! ServerAccepted(server)
            selfRef ! protocol.Envelope(0L, 0L, protocol.Started)

            // loop is broken by EOFException or SocketException
            while (true) {
              val wire = server.receive()
              log.debug("  server received from child: {}", wire)
              selfRef ! protocol.Envelope(wire)
            }
          } finally {
            if (!server.isClosed)
              server.close()
          }
        } catch {
          case e: SocketException =>
            // on socket close, this is expected; don't let it throw up to the default handler
            log.debug("  server socket appears to be closed, {}", e.getMessage())
          case e: EOFException =>
            // expected if the other side exits cleanly
            log.debug("  server socket EOF, {}", e.getMessage)
        } finally {
          log.debug("  server actor thread ending")
          selfRef ! protocol.Envelope(0L, 0L, protocol.Stopped)
          log.debug("  sent protocol.Stopped from server actor thread")
        }
      }
    })
  }

  def destroy() = {
    log.debug("  destroying")
    pendingReplies.foreach {
      case (key, requestor) =>
        log.debug("  sending error reply to a pending server request {} {}", key, requestor)
        requestor.ref ! protocol.ErrorResponse("ServerActor died before it replied to request")
    }
    pendingReplies = Map.empty

    serverOption foreach { server =>
      if (!server.isClosed)
        server.close()
    }
    if (!pool.isShutdown())
      pool.shutdown()
    if (!pool.isTerminated())
      pool.awaitTermination(2000, TimeUnit.MILLISECONDS)
  }

  override def postStop() = {
    log.debug("postStop")
    destroy()
  }
}
