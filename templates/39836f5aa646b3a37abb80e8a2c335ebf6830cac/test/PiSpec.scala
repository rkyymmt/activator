import PiScala.{PiApproximation, Calculate}

import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.concurrent._
import org.scalatest.matchers.ShouldMatchers
import akka.actor.{Actor, Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}

class PiSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with ShouldMatchers with FlatSpec with BeforeAndAfterAll with Eventually {

  def this() = this(ActorSystem("PiSpec"))
  
  override def afterAll {
    system.shutdown()
  }

  "A Pi Actor System actor" should "start with the right numbers" in {
    system.actorOf(Props(new PiScala.Master(4, 10000, 10000, self)), name = "master") ! Calculate
    expectMsgType[PiApproximation].pi.toString should startWith ("3.1415")
  }

}
