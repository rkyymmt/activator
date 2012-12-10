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

      val name = Await.result(child ? Protocol.NameRequest, 10 seconds) match {
        case Protocol.NameResponse(n) => n
      }
      println("Project is: " + name)

      //val compiled = Await.result(child ? Protocol.CompileRequest, 60 seconds) match {
      //  case Protocol.CompileResponse => true
      //}

      //println("compiled=" + compiled)

    } finally {
      system.stop(child)
    }
  } finally {
    system.shutdown()
  }
}
