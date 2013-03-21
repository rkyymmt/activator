package sbtchild

import com.typesafe.sbtchild._
import java.io.File
import akka.actor._
import akka.pattern._
import akka.dispatch._
import concurrent.duration._
import concurrent.Await
import akka.util.Timeout
import snap.tests._

class PlayProject extends IntegrationTest {
  val system = ActorSystem("ManualTest")

  def newSbtChild(dir: File) = SbtChild(system, dir, new SbtChildLauncher(configuration))

  def startup(dir: File): Unit = {
    val child = newSbtChild(dir)
    try {
      implicit val timeout = Timeout(120.seconds)
      val startupReboot = Await.result(child ? protocol.NameRequest(sendEvents = false), timeout.duration) match {
        case protocol.NameResponse(n) => {
          throw new Exception("We expect the first run of the sbt project to fail, while we load the plugin.")
        }
        case protocol.ErrorResponse(error) =>
          true
      }
      println("Project has rebooted with new plugin.")
    } catch {
      case e: Exception => // Ignore
    } finally {
      system.stop(child)
    }
  }

  try {
    // TODO - Create project here, rather than rely on it created by test harness....
    val dir = new File("dummy")
    makeDummyPlayProject(dir)
    startup(dir)
    // TODO - We know the child will die and need to be restarted, let's figure out how to make sure it happens
    val child = newSbtChild(dir)
    try {
      implicit val timeout = Timeout(120.seconds)
      val name = Await.result(child ? protocol.NameRequest(sendEvents = false), timeout.duration) match {
        case protocol.NameResponse(n) => {
          n
        }
        case protocol.ErrorResponse(error) =>
          throw new Exception("Failed to get project name: " + error)
      }
      println("Project is: " + name)
      val runFuture = child ? protocol.RunRequest(sendEvents = false, mainClass = None)

      // kill off the run
      child ! protocol.CancelRequest

      val run = Await.result(runFuture, timeout.duration) match {
        case protocol.RunResponse(success, "run") => {
          success
        }
        case protocol.ErrorResponse(error) =>
          throw new Exception("Failed to run: " + error)
      }
      println("run=" + run)
    } finally {
      system.stop(child)
    }
  } finally {
    system.shutdown()
  }
}
