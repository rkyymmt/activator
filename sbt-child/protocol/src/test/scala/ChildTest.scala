/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
import org.junit.Assert._
import org.junit._
import com.typesafe.sbtchild._
import com.typesafe.sbtchild.Protocol._
import java.io.File
import akka.actor._
import akka.dispatch._
import akka.util.duration._
import akka.pattern._
import akka.util.Timeout

class ChildTest {

  @Test
  def testTalkToChild(): Unit = {
    implicit val timeout = Timeout(10 seconds)

    val system = ActorSystem("test-talk-to-child")
    try {
      val child = SbtChild(system, new File("."))

      try {
        val name = Await.result(child ? NameRequest, 10 seconds) match {
          case NameResponse(n) => n
        }
        assertEquals("root", name)

        val name2 = Await.result(child ? NameRequest, 10 seconds) match {
          case NameResponse(n) => n
        }
        assertEquals("root", name2)

      } finally {
        system.stop(child)
      }

    } finally {
      system.shutdown()
    }
  }
}
