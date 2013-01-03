/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
import org.junit.Assert._
import org.junit._
import com.typesafe.sbtchild._
import com.typesafe.sbtchild.protocol._
import java.io.File
import akka.actor._
import akka.dispatch._
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern._
import akka.util.Timeout

class ChildTest {

  // TODO this needs to become an integration test so it can find the sbt executable
  //@Test
  def testTalkToChild(): Unit = {
    implicit val timeout = Timeout(10 seconds)

    val system = ActorSystem("test-talk-to-child")
    try {
      val child = SbtChild(system, new File("."), HavocsSbtChildProcessmaker)

      try {
        val name = Await.result(child ? NameRequest, 10 seconds) match {
          case NameResponse(n, logs) => n
        }
        assertEquals("root", name)

        val name2 = Await.result(child ? NameRequest, 10 seconds) match {
          case NameResponse(n, logs) => n
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
