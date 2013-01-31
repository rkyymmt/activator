/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
import com.typesafe.sbtchild._

import akka.actor._
import java.util.concurrent.atomic.AtomicInteger

class MockSbtChildFactory extends SbtChildFactory {
  def creator = new Actor() {
    override def receive = {
      case req: protocol.Request => req match {
        case protocol.NameRequest =>
          sender ! protocol.NameResponse("foo", Nil)
        case protocol.CompileRequest =>
          sender ! protocol.CompileResponse(Nil)
        case protocol.RunRequest =>
          sender ! protocol.RunResponse(Nil)
      }
    }
  }

  val childNum = new AtomicInteger(1)

  override def newChild(actorFactory: ActorRefFactory): ActorRef = {
    actorFactory.actorOf(Props(creator = creator), "mock-sbt-child-" + childNum.getAndIncrement())
  }
}
