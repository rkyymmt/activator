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
    createFile(build, s"""
name := "${relativeDir}"

libraryDependencies += "com.novocode" % "junit-interface" % "0.7" % "test"
""")

    val scalaSource = new File(dir, "src/main/scala")
    if (!scalaSource.isDirectory()) scalaSource.mkdirs()
    val main = new File(scalaSource, "hello.scala")
    createFile(main, "object Main extends App { println(\"Hello World\") }\n")

    val testSource = new File(dir, "src/test/scala")
    if (!testSource.isDirectory()) testSource.mkdirs()
    val tests = new File(testSource, "tests.scala")
    createFile(tests, """
import org.junit.Assert._
import org.junit._

class OnePassOneFailTest {
    @Test
    def testThatShouldPass: Unit = {
    }

    @Test
    def testThatShouldFail: Unit = {
        assertTrue("this is not true", false)
    }
}

class OnePassTest {
    @Test
    def testThatShouldPass: Unit = {
    }
}

class OneFailTest {
    @Test
    def testThatShouldFail: Unit = {
        assertTrue("this is not true", false)
    }
}
""")

    dir
  }

  def makeDummySbtProjectWithBrokenBuild(relativeDir: String): File = {
    val dir = makeDummySbtProject(relativeDir)

    val build = new File(dir, "build.sbt")
    createFile(build, "BLARG := \"" + relativeDir + "\"\n")

    dir
  }

  def makeDummySbtProjectWithNoMain(relativeDir: String): File = {
    val dir = makeDummySbtProject(relativeDir)

    val main = new File(dir, "src/main/scala/hello.scala")
    // doesn't extend App
    createFile(main, "object Main { println(\"Hello World\") }\n")

    dir
  }

  private class TestRequestActor(dummy: File) extends Actor with ActorLogging {
    val child = SbtChild(context, dummy, DebugSbtChildProcessMaker)

    var recorded: Seq[protocol.Message] = Nil
    var requestor: Option[ActorRef] = None
    var done = false

    override def receive = {
      case m: protocol.Message =>
        recorded = recorded :+ m
        m match {
          case r: protocol.Response =>
            done = true
            requestor.foreach(_ ! recorded)
          case _ =>
        }
      case "get" =>
        if (done)
          sender ! recorded
        else
          requestor = Some(sender)
    }
  }

  private def requestTest(dummy: File)(sendRequest: (ActorRef, ActorContext) => Unit)(checkResults: Seq[protocol.Message] => Unit): Unit = {
    implicit val timeout = Timeout(120.seconds)

    val system = ActorSystem("test-" + dummy.getName)
    try {
      val req = system.actorOf(Props(new TestRequestActor(dummy) {
        sendRequest(child, context)
      }))

      Await.result(req ? "get", timeout.duration) match {
        case s: Seq[_] =>
          checkResults(s.collect({ case m: protocol.Message => m }))
        case whatever => throw new AssertionError("unexpected reply from TestRequestActor: " + whatever)
      }

    } finally {
      system.shutdown()
    }
  }

  private def noLogs(results: Seq[protocol.Message]): Seq[protocol.Message] = {
    results flatMap {
      case e: protocol.LogEvent => Seq.empty
      case m: protocol.Message => Seq(m)
    }
  }

  // this is just an ad hoc attempt to make the event order more
  // deterministic. We assume a stable sort.
  private implicit val messageOrdering: Ordering[protocol.Message] = new Ordering[protocol.Message]() {
    override def compare(a: protocol.Message, b: protocol.Message): Int = {
      (a, b) match {
        // sort test events by the test name since they
        // otherwise arrive in undefined order
        case (a: protocol.TestEvent, b: protocol.TestEvent) =>
          a.name.compareTo(b.name)
        // leave it alone
        case (a, b) =>
          0
      }
    }
  }

  @Test
  def testRunTests(): Unit = {
    requestTest(makeDummySbtProject("testing123")) { (child, context) =>
      implicit val self = context.self
      child ! TestRequest(sendEvents = true)
    } { results =>
      noLogs(results).sorted match {
        case Seq(TestEvent("OneFailTest.testThatShouldFail",
          Some("this is not true"), TestFailed, Some("this is not true")),
          TestEvent("OnePassOneFailTest.testThatShouldFail",
            Some("this is not true"), TestFailed, Some("this is not true")),
          TestEvent("OnePassOneFailTest.testThatShouldPass", None, TestPassed, None),
          TestEvent("OnePassTest.testThatShouldPass", None, TestPassed, None),
          ErrorResponse("exception during sbt task: Incomplete: null")) =>
        // yay!
        case whatever => throw new AssertionError("got wrong results: " + whatever)
      }
    }
  }

  @Test
  def testTalkToChild(): Unit = {
    implicit val timeout = Timeout(60.seconds)

    val dummy = makeDummySbtProject("talkToChild")

    val system = ActorSystem("test-talk-to-child")
    try {
      val child = SbtChild(system, dummy, DebugSbtChildProcessMaker)

      try {
        val name = Await.result(child ? NameRequest(sendEvents = false), timeout.duration) match {
          case NameResponse(n) => n
        }
        assertEquals("talkToChild", name)

        val name2 = Await.result(child ? NameRequest(sendEvents = false), timeout.duration) match {
          case NameResponse(n) => n
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
        Await.result(child ? RunRequest(sendEvents = false), timeout.duration) match {
          case RunResponse(success) =>
          case whatever => throw new AssertionError("did not get RunResponse")
        }
      } finally {
        system.stop(child)
      }

    } finally {
      system.shutdown()
    }
  }

  @Test
  def testBrokenBuild(): Unit = {
    implicit val timeout = Timeout(60.seconds)

    val dummy = makeDummySbtProjectWithBrokenBuild("brokenBuild")

    val system = ActorSystem("test-broken-build")
    try {
      val child = SbtChild(system, dummy, DebugSbtChildProcessMaker)

      try {
        Await.result(child ? RunRequest(sendEvents = false), timeout.duration) match {
          case ErrorResponse(message) if message.contains("sbt process never got in touch") =>
          case whatever => throw new AssertionError("unexpected result sending RunRequest to broken build: " + whatever)
        }
      } finally {
        system.stop(child)
      }

    } finally {
      system.shutdown()
    }
  }

  @Test
  def testMissingMain(): Unit = {
    implicit val timeout = Timeout(60.seconds)

    val dummy = makeDummySbtProjectWithNoMain("noMain")

    val system = ActorSystem("test-no-main")
    try {
      val child = SbtChild(system, dummy, DebugSbtChildProcessMaker)

      try {
        Await.result(child ? RunRequest(sendEvents = false), timeout.duration) match {
          case ErrorResponse(message) if message.contains("during sbt task: Incomplete") =>
          case whatever => throw new AssertionError("unexpected result sending RunRequest to app with no main method: " + whatever)
        }
      } finally {
        system.stop(child)
      }

    } finally {
      system.shutdown()
    }
  }
}
