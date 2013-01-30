package com.typesafe.sbtchild

import akka.actor._
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.ServerSocket
import java.net.SocketException

// these are messages to/from the actor; we also just
// forward the Envelope directly from the wire
sealed trait ServerActorRequest
case class SubscribeServer(ref: ActorRef) extends ServerActorRequest
case class UnsubscribeServer(ref: ActorRef) extends ServerActorRequest
// Ask the server to accept connections
case object StartServer extends ServerActorRequest
// sent by the server to itself when it accepts the connection
case class ServerAccepted(server: ipc.Server) extends ServerActorRequest

class ServerActor(serverSocket: ServerSocket) extends Actor with ActorLogging {
  var subscribers: Set[ActorRef] = Set.empty

  var serverOption: Option[ipc.Server] = None

  val pool = NamedThreadFactory.newPool("ServerActor")

  val selfRef = context.self

  var pendingReplies = Map.empty[Long, ActorRef]

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
        sender ! protocol.ErrorResponse("sbt socket is disconnected", Nil)
      } else {
        val server = serverOption.getOrElse(throw new Exception("Made request before server accept"))
        try {
          val requestSerial = server.sendJson(req)
          val pair = (requestSerial -> sender)
          pendingReplies += pair
          log.debug("  added to pending replies: {}", pair)
        } catch {
          case e: Exception =>
            log.warning("failed to send message to child process", e)
            sender ! protocol.ErrorResponse(e.getMessage, Nil)
        }
      }
    }

    case Terminated(ref) =>
      subscribers = subscribers - ref

    case e: protocol.Envelope =>
      if (e.replyTo != 0L) {
        // replies
        log.debug("  dispatching pending reply to {}", e.replyTo)
        val requestor = pendingReplies.get(e.replyTo).getOrElse(throw new RuntimeException("Nobody was waiting for " + e))
        pendingReplies = pendingReplies - e.replyTo
        requestor ! e.content
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

            while (!server.isClosed) {
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
    pendingReplies.foreach { tup =>
      log.debug("  sending error reply to a pending server request {}", tup)
      tup._2 ! protocol.ErrorResponse("ServerActor died before it replied to request", Nil)
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
