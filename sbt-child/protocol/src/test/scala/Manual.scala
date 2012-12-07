import com.typesafe.sbtchild._
import Protocol._
import java.io.File

object Main extends App {
  if (args.length < 1)
    throw new Exception("Specify directory")
  val child = SbtChild(new File(args(0)))

  child.server.requestName()
  val name = child.server.receiveName()
  println("Project is: " + name)

  child.server.requestCompile()
  child.server.receiveCompile()
  println("compiled?")
}
