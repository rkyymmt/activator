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

class IdeaTest extends IntegrationTest {
  val system = ActorSystem("IdeaTest")

  def newSbtChild(dir: File) = SbtChild(system, dir, new SbtChildLauncher(configuration))

  try {
    // TODO - Create project here, rather than rely on it created by test harness....
    val dir = new File("idea-dummy")
    makeDummySbtProject(dir)

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
      val ideaFuture = child ? protocol.GenericRequest(sendEvents = false, name = "gen-idea", params = Map.empty)

      Await.result(ideaFuture, timeout.duration) match {
        case protocol.GenericResponse("gen-idea", _) =>
        case protocol.ErrorResponse(error) =>
          throw new Exception("Failed to generate idea stuff: " + error)
      }
    } finally {
      system.stop(child)
    }
    if (!(new File(dir, ".idea")).isDirectory())
      throw new AssertionError("No .idea file created in " + dir)

  } finally {
    system.shutdown()
  }
}
