package actors

import akka.actor._
import akka.testkit._

import org.specs2.mutable._
import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._

class UsersActorSpec extends Specification with NoTimeConversions {

  sequential // forces all tests to be run sequentially

  class Wrapper(target: ActorRef) extends Actor {
    def receive = {
      case x => target forward x
    }
  }

  "A UsersActor receiving a StockUpdate" should {
    val uuid = java.util.UUID.randomUUID.toString
    val symbol = "ABC"
    val price = 123

    "tell all its children about the message" in new AkkaTestkitSpecs2Support {
      // Set up a test probe to pass into the actor...
      val probe1 = TestProbe()

      // Swap out the creator method with something that forwards the message through
      // our probe here (children are created internal to the actor, so we can't just
      // say "userActor.context.addChild" and touch it directly...)
      class UsersActorWithTestProbe extends UsersActor {
        override def getUserActorCreator(listen:Listen) = new akka.japi.Creator[Actor] {
          def create() = new Wrapper(probe1.ref)
        }
      }

      // create the actor under test
      val actor = system.actorOf(Props(new UsersActorWithTestProbe))

      // Kick off the creation of the internal user actor (and test probe)...
      val out = new StubOut()
      actor ! Listen(uuid, out)

      // send off the stock update...
      val stockUpdate = StockUpdate(symbol, price)
      actor ! stockUpdate

      // Expect the probes to get it.
      val actual = probe1.expectMsg(500 millis, stockUpdate)
      actual must beTheSameAs(stockUpdate)
    }
  }

  "A UsersActor receiving a Listen" should {
    val uuid = java.util.UUID.randomUUID.toString

    "create a new child UserActor" in new AkkaTestkitSpecs2Support {
      // no real need for a probe here, but it makes the test happy
      val probe1 = TestProbe()

      var creatorMethodCalled = false
      class UsersActorWithFakeCreator extends UsersActor {
        override def getUserActorCreator(listen:Listen) = new akka.japi.Creator[Actor] {
          def create() = {
            creatorMethodCalled = true
            new Wrapper(probe1.ref)
          }
        }
      }

      // Create the actor under test
      val actor = system.actorOf(Props(new UsersActorWithFakeCreator))

      // Send the Listen...
      val out = new StubOut()
      actor ! Listen(uuid, out)

      // ...and we expect a new child actor.
      creatorMethodCalled must beTrue
    }
  }

  "A UsersActor receiving a WatchStock" should {
    val uuid = java.util.UUID.randomUUID.toString
    val symbol = "ABC"

    "tell the child with the UUID about the message" in new AkkaTestkitSpecs2Support {
      val probe1 = TestProbe()

      class UsersActorWithTestProbe extends UsersActor {
        override def getUserActorCreator(listen:Listen) = new akka.japi.Creator[Actor] {
          def create() = new Wrapper(probe1.ref)
        }
      }

      val actor = system.actorOf(Props(new UsersActorWithTestProbe))

      val out = new StubOut()
      actor ! Listen(uuid, out)

      val watchStock = WatchStock(uuid, symbol)
      actor ! watchStock

      val actual = probe1.expectMsg(500 millis, watchStock)
      actual must beTheSameAs(watchStock)
    }
  }

  "A UsersActor receiving a UnwatchStock" should {
    val uuid = java.util.UUID.randomUUID.toString
        val symbol = "ABC"

    "tell the child with the UUID about the message" in new AkkaTestkitSpecs2Support {
      val probe1 = TestProbe()

      class UsersActorWithTestProbe extends UsersActor {
        override def getUserActorCreator(listen:Listen) = new akka.japi.Creator[Actor] {
          def create() = new Wrapper(probe1.ref)
        }
      }

      val actor = system.actorOf(Props(new UsersActorWithTestProbe))

      val out = new StubOut()
      actor ! Listen(uuid, out)

      val unwatchStock = UnwatchStock(uuid, symbol)
      actor ! unwatchStock

      val actual = probe1.expectMsg(500 millis, unwatchStock)
      actual must beTheSameAs(unwatchStock)
    }
  }
}
