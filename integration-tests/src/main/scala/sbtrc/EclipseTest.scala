package sbtrc

import com.typesafe.sbtrc._
import java.io.File
import akka.actor._
import akka.pattern._
import akka.dispatch._
import concurrent.duration._
import concurrent.Await
import akka.util.Timeout
import snap.tests._

class EclipseTest extends IntegrationTest {
  val system = ActorSystem("EclipseTest")

  def newSbtChild(dir: File) = SbtProcess(system, dir, new DefaultSbtProcessLauncher(configuration))

  try {
    // TODO - Create project here, rather than rely on it created by test harness....
    val dir = new File("eclipse-dummy")
    makeDummySbtProject(dir)

    val child = newSbtChild(dir)
    try {
      implicit val timeout = Timeout(300.seconds)
      val name = Await.result(child ? protocol.NameRequest(sendEvents = false), timeout.duration) match {
        case protocol.NameResponse(n) => {
          n
        }
        case protocol.ErrorResponse(error) =>
          throw new Exception("Failed to get project name: " + error)
      }
      println("Project is: " + name)
      val eclipseFuture = child ? protocol.GenericRequest(sendEvents = false, name = "eclipse", params = Map.empty)

      Await.result(eclipseFuture, timeout.duration) match {
        case protocol.GenericResponse("eclipse", _) =>
        case protocol.ErrorResponse(error) =>
          throw new Exception("Failed to generate eclipse stuff: " + error)
      }
    } finally {
      system.stop(child)
    }
    if (!(new File(dir, ".project")).exists)
      throw new AssertionError("No .project file created")
    if (!(new File(dir, ".classpath")).exists)
      throw new AssertionError("No .classpath file created")
  } finally {
    system.shutdown()
    system.awaitTermination()
  }
}
