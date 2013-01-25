package controllers.api

import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsString, JsObject, JsArray, JsNumber}
import play.api.Play
import sys.process.Process
import java.io.File
import play.api.mvc.{ResponseHeader, SimpleResult}
import play.api.libs.iteratee.{ Enumerator, Enumeratee }
import akka.actor.{Actor,ActorRef, Props}
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Concurrent.Channel
import concurrent.Future
import akka.pattern.ask

/** This controller handles piping events out to folks. */
object Events extends Controller {
  // Enumerator that starts a new actor listening to the event stream per-event-stream request.
  // What's annoying here is we have to *manually* create a context that is closed over by three closures
  // and used rather than just extend a class.
  private val events: Enumerator[String] = EventStreamEnumerator()
  
  // TODO - We should probably take a SSEvent() class here, so we can support all the spec, including ids and retry and such.
  private val toEventSource = Enumeratee.map[String] { msg =>
    // We can't allow end lines in the messages, so we split on them, and then feed data.
    // TODO - Better end-line splitting.
    msg.split("[\\r]?[\\n]").mkString("data: ", "\ndata: ", "\n\n\n")
  }


  // Action that returns a new event source stream.    
  def eventSource = Action {
    Ok.stream(events &> toEventSource).withHeaders(CONTENT_TYPE -> "text/event-stream")
  }
  
  def test = Action {
    snap.Akka.events ! """{ "type": "test", "msg": "TEST EVENT" }"""
    Ok("OK")
  }

  def kill = Action {
    snap.Akka.events ! "Kill Children"
    Ok("OK")
  }


}

// This object constructs a new Enumerator that pushes the event JSON strings it receives through
// play's iteratee framework.
object EventStreamEnumerator {
  // Here we create an enumerator where we have a context closed over by all three closures so that
  // we can reference our start from all three closures.
  // Most importantly, we attempt to cleanup our garbage.
  // However, onComplete and onError are *not* being called
  // when a connection is closed.   have to look into that.
  def apply(): Enumerator[String] = {
    val context = new EventActorContext(null)
    Concurrent.unicast(
      onStart = { channel =>
        println("Making downstream event handler!")
        implicit val t: akka.util.Timeout = akka.util.Timeout(concurrent.duration.FiniteDuration(10, "s"))
        context.actorListener = (snap.Akka.events ? Props(new EventStreamActor(channel))).mapTo[ActorRef]
      }, 
      onComplete = () => context.killListener(), 
      onError = (msg, input) => context.killListener())
  }


  private class EventActorContext(var actorListener: Future[ActorRef]) {
    // Called when the client closes an event stream, so we can clean up.
    def killListener(): Unit = {
      import snap.Akka.system.dispatcher
      println("Stoping event channel - client no longer listening.")
      actorListener map (_ ! akka.actor.PoisonPill)
    }
  }
  // Simple actor that just pushes messages it receives into the message channel.
  private class EventStreamActor(channel: Channel[String]) extends Actor {
    // TODO - Close the channel sometime?
    def receive: Receive = {
      case msg: String => channel push msg
    }
    // Ensure we stop the channel, if we can.
    // TODO - Ignore failure?
    override def postStop():Unit = channel.end()
  }

}
