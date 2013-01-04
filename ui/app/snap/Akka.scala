package snap

// This guy stores the Akka we use for eventing.
object Akka {
  // TODO - use my thread context
  val system = withContextCl(akka.actor.ActorSystem())
  
  val events = system.actorOf(akka.actor.Props[EventActor]())
  
  
  private def withContextCl[A](f: =>A): A = {
    val cl = Thread.currentThread.getContextClassLoader
    Thread.currentThread.setContextClassLoader(this.getClass.getClassLoader)
    try f
    finally {
      Thread.currentThread.setContextClassLoader(cl)
    }
  }
}

import akka.actor.{Actor, ActorRef, Props}

// TODO - Cleanup and thread stuff.
class EventActor extends Actor {
  var listeners: Seq[ActorRef] = Nil
  
  def receive: Receive = {
    case newListenerProps: Props =>
      val newListener = context actorOf newListenerProps
      listeners +:= newListener
    case msg: String =>
      println("MASTER EVENT HANDLER GOT: "  + msg)
      listeners foreach (_ ! msg)
  }
}