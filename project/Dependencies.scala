import sbt._
import PlayProject._
import Keys._

object SnapDependencies {
  val sbtVersion = "0.12.1"

  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion % "provided"


  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }
}
