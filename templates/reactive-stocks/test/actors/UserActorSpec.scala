package actors

import akka.actor._
import akka.testkit._

import org.codehaus.jackson.JsonNode

import org.specs2.mutable._
import org.specs2.time.NoTimeConversions

import play.mvc.WebSocket

import scala.concurrent._
import scala.concurrent.duration._

// Using http://blog.xebia.com/2012/10/01/testing-akka-with-specs2/
/* A tiny class that can be used as a Specs2 'context'. */
abstract class AkkaTestkitSpecs2Support extends TestKit(ActorSystem())
with After
with ImplicitSender {
  // make sure we shut down the actor system after all tests have run
  def after {
    system.shutdown()
  }
}


class UserActorSpec extends Specification with NoTimeConversions {

  sequential // forces all tests to be run sequentially

  class StubOut() extends WebSocket.Out[JsonNode]() {
    var expected:JsonNode = null

    def write(node: JsonNode) {
      expected = node
    }

    def close() {}
  }

  "A user actor receiving StockUpdate" should {

    val uuid = java.util.UUID.randomUUID.toString
    val symbol = "ABC"
    val price = 123

    "write out a stock that is in the map" in new AkkaTestkitSpecs2Support {
      val out = new StubOut()
      val userActorRef = TestActorRef[UserActor](Props(new UserActor(uuid, out)))
      val userActor = userActorRef.underlyingActor

      // Tell the user actor that it has this stock in its internal map...
      val stockActorRef = TestActorRef[StockActor](Props(new StockActor(symbol)))
      userActor.stocks = Map(symbol -> stockActorRef)

      // send off the stock update...
      userActor.receive(StockUpdate(symbol, price))

      // ...and expect it to be a JSON node.
      out.expected should not beNull
    }

    "not write out a stock that is NOT in the map" in new AkkaTestkitSpecs2Support {
      val out = new StubOut()
      val userActorRef = TestActorRef[UserActor](Props(new UserActor(uuid, out)))
      val userActor = userActorRef.underlyingActor

      // send off the stock update...
      userActor.receive(StockUpdate(symbol, price))

      // ...and expect null.
      out.expected should beNull
    }
  }

  "A UserActor receiving WatchStock" should {
    val uuid = java.util.UUID.randomUUID.toString
    val symbol = "ABC"

    "write out to a web socket" in new AkkaTestkitSpecs2Support {
      import utils.Global._

      // Do a straight integration test here...
      val out = new StubOut()
      val userActorRef = system.actorOf(Props(new UserActor(uuid, out)))
      stockHolderActor = system.actorOf(Props(new StockHolderActor()))

      within (5 seconds) {
        userActorRef ! WatchStock(uuid, symbol)
        expectNoMsg // block for 5 seconds
      }
      out.expected should not beNull
    }
  }

  "A UserActor receiving UnwatchStock" should {

    val uuid = java.util.UUID.randomUUID.toString
    val symbol = "ABC"

    "remove the stock" in new AkkaTestkitSpecs2Support {
      val out = new StubOut()

      val userActorRef = TestActorRef[UserActor](Props(new UserActor(uuid, out)))
      val userActor = userActorRef.underlyingActor
      val stockActorRef = TestActorRef[StockActor](Props(new StockActor(symbol)))

      userActor.stocks = Map(symbol -> stockActorRef)

      userActor.receive(UnwatchStock(uuid, symbol))

      userActor.stocks must not haveKey(symbol)
    }
  }

}
