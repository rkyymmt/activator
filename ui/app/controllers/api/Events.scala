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

/** This controller handles piping events out to folks. */
object Events extends Controller {
  
  private val eventSourceHeader = ResponseHeader(OK, Map(
          CONTENT_LENGTH -> "-1",
          CONTENT_TYPE -> "text/event-stream"
      )) 
  // Enumerator that starts a new actor listening to the event stream per-event-stream request.
  private val events: Enumerator[String] = Concurrent.unicast(
      onStart = { channel =>
        println("Making downstream event handler!")
        snap.Akka.events ! Props(new EventStreamActor(channel))
      }, 
      onComplete = () => println("Events enumerator stopped!"), 
      onError = (msg, input) => println("Events enumerator errors!: " + msg))
      
  private val toEventSource = Enumeratee.map[String](msg => s"data: ${msg}\n\n\n")
  // Action that returns a new event source stream.    
  def eventSource = Action {
    SimpleResult(
      header = eventSourceHeader,
      events &> toEventSource
    )
  }
  
  def test = Action {
    snap.Akka.events ! """{ "type": "test", "msg": "TEST EVENT" }"""
    Ok("OK")
  }
  
  // Simple actor that just pushes messages to teh pushee for an enumerator.
  class EventStreamActor(channel: Channel[String]) extends Actor {
    // TODO - Close the channel sometime?
    def receive: Receive = {
      case msg: String     => 
        println("DOWNSTREAM EVENT HANDLER GOT: "+msg)
        channel push msg
    }
  }
}