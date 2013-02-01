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

  def createFile(name: java.io.File, content: String): Unit = {
    val writer = new java.io.FileWriter(name)
    try writer.write(content)
    finally writer.close()
  }

  /** Creates a dummy project we can run sbt against. */
  def makeDummySbtProject(relativeDir: String): File = {
    val dir = new File(new File("sbt-child/parent/target/scratch"), relativeDir)
    if (!dir.isDirectory()) dir.mkdirs()

    val project = new File(dir, "project")
    if (!project.isDirectory()) project.mkdirs()

    val props = new File(project, "build.properties")
    createFile(props, "sbt.version=" + snap.properties.SnapProperties.SBT_VERSION)

    val build = new File(dir, "build.sbt")
    createFile(build, "name := \"" + relativeDir + "\"\n")

    val scalaSource = new File(dir, "src/main/scala")
    if (!scalaSource.isDirectory()) scalaSource.mkdirs()
    val main = new File(scalaSource, "hello.scala")
    createFile(main, "object Main extends App { println(\"Hello World\") }\n")
    dir
  }

  @Test
  def testTalkToChild(): Unit = {
    implicit val timeout = Timeout(60.seconds)

    val dummy = makeDummySbtProject("talkToChild")

    val system = ActorSystem("test-talk-to-child")
    try {
      val child = SbtChild(system, dummy, DebugSbtChildProcessMaker)

      try {
        val name = Await.result(child ? NameRequest, timeout.duration) match {
          case NameResponse(n, logs) => n
        }
        assertEquals("talkToChild", name)

        val name2 = Await.result(child ? NameRequest, timeout.duration) match {
          case NameResponse(n, logs) => n
        }
        assertEquals("talkToChild", name2)

      } finally {
        system.stop(child)
      }

    } finally {
      system.shutdown()
    }
  }

  object EchoHelloChildProcessMaker extends SbtChildProcessMaker {

    def arguments(port: Int): Seq[String] = Seq("echo", "Hello World")
  }

  @Test
  def testChildProcessNeverConnects(): Unit = {
    val system = ActorSystem("test-child-never-connects")

    val child = SbtChild(system, new File("."), EchoHelloChildProcessMaker)

    // the test is that the child should die on its own so actor system shutdown should work

    system.shutdown()
  }

  @Test
  def testRunChild(): Unit = {
    implicit val timeout = Timeout(60.seconds)

    val dummy = makeDummySbtProject("runChild")

    val system = ActorSystem("test-run-child")
    try {
      val child = SbtChild(system, dummy, DebugSbtChildProcessMaker)

      try {
        Await.result(child ? RunRequest, timeout.duration) match {
          case RunResponse(logs) =>
          case whatever => throw new AssertionError("did not get RunResponse")
        }
      } finally {
        system.stop(child)
      }

    } finally {
      system.shutdown()
    }
  }
}
