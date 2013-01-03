package sbtchild

import com.typesafe.sbtchild._
import java.io.File
import akka.actor._
import akka.pattern._
import akka.dispatch._
import akka.util.duration._
import akka.util.Timeout

class CanLaunchThroughSbtLauncher extends xsbti.AppMain {

  def run(configuration: xsbti.AppConfiguration) = 
    try withContextClassloader(test(configuration))
    catch {
      case t: Exception =>    
        t.printStackTrace()
        Exit(1)
    }
  
  def withContextClassloader[A](f: => A): A = {
    val current = Thread.currentThread
    val old = current.getContextClassLoader
    current setContextClassLoader getClass.getClassLoader
    try f
    finally current setContextClassLoader old
  }

  def test(conf: xsbti.AppConfiguration): xsbti.Exit =  {
    val system = ActorSystem("ManualTest")
    try {
      // TODO - Create project here, rather than rely on it created by test harness....
      val dir = new File("dummy")
      dir.mkdirs()
      val project = new File(dir, "project")
      project.mkdirs()
      val props = new File(project, "build.properties")
      val tmp = {
        val writer = new java.io.FileWriter(props)
        try writer.write("sbt.version="+snap.properties.SnapProperties.SBT_VERSION)
        finally writer.close()
      }
      val child = SbtChild(system, dir, new SbtChildLauncher(conf))
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
    Exit(0)
  }

  case class Exit(code: Int) extends xsbti.Exit
}
