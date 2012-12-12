import com.typesafe.sbtchild._
import java.io.File
import akka.actor._
import akka.pattern._
import akka.dispatch._
import akka.util.duration._
import akka.util.Timeout

object Main extends App {
  if (args.length < 1)
    throw new Exception("Specify directory")

  val system = ActorSystem("ManualTest")

  try {
    val child = SbtChild(system, new File(args(0)))
    try {

      implicit val timeout = Timeout(60 seconds)

      val name = Await.result(child ? protocol.NameRequest, 60 seconds) match {
        case protocol.NameResponse(n, logs) => {
          System.err.println("logs=" + logs)
          n
        }
        case protocol.ErrorResponse(error, logs) =>
          throw new Exception("Failed to get project name: " + error)
      }
      println("Project is: " + name)

      val compiled = Await.result(child ? protocol.CompileRequest, 60 seconds) match {
        case protocol.CompileResponse(logs) => {
          System.err.println("logs=" + logs)
          true
        }
        case protocol.ErrorResponse(error, logs) =>
          System.err.println("Failed to compile: " + error)
          false
      }

      println("compiled=" + compiled)

    } finally {
      system.stop(child)
    }
  } finally {
    system.shutdown()
  }
}
