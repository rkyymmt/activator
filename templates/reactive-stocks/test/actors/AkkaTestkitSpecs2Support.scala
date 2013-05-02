package actors

import akka.actor._
import akka.testkit._

import org.specs2.mutable._

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
