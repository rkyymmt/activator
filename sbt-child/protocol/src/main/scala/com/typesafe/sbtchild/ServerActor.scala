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
case object StartServer extends ServerActorRequest
case class ServerAccepted(server: IPC.Server) extends ServerActorRequest
case class MakeServerRequest(replyTo: ActorRef, request: Protocol.Request) extends ServerActorRequest

class ServerActor(serverSocket: ServerSocket) extends Actor {
  var subscribers: Set[ActorRef] = Set.empty

  var serverOption: Option[IPC.Server] = None

  val pool = Executors.newCachedThreadPool()

  val selfRef = context.self

  var pendingReplies = Map.empty[Long, ActorRef]

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
      case MakeServerRequest(replyTo, r) =>
        val server = serverOption.getOrElse(throw new Exception("Made request before server accept"))
        val requestSerial = server.sendSerialized(r)
        pendingReplies += (requestSerial -> replyTo)
    }

    case Terminated(ref) =>
      subscribers = subscribers - ref

    case e: Protocol.Envelope =>
      if (e.replyTo != 0L) {
        // replies
        pendingReplies.get(e.replyTo) foreach { requestor =>
          pendingReplies = pendingReplies - e.replyTo
          requestor ! e.content
        }
      } else {
        // events
        for (s <- subscribers) {
          s ! e.content
        }
        e.content match {
          case Protocol.Stopped =>
            context.stop(self)
          case _ =>
        }
      }
  }

  def start(): Unit = {
    pool.execute(new Runnable() {
      override def run = {
        val server = IPC.accept(serverSocket)
        if (!serverSocket.isClosed())
          serverSocket.close() // we only want one connection
        selfRef ! ServerAccepted(server)
        selfRef ! Protocol.Envelope(0L, 0L, Protocol.Started)
        try {
          while (!server.isClosed) {
            val wire = server.receive()
            selfRef ! Protocol.Envelope(wire)
          }
        } catch {
          case e: SocketException =>
            // on socket close, this is expected; be sure it's closed though
            if (!server.isClosed)
              server.close()
        } finally {
          selfRef ! Protocol.Envelope(0L, 0L, Protocol.Stopped)
        }
      }
    })
  }

  def destroy() = {
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
    destroy()
  }
}
