import sbt._
import PlayProject._
import Keys._

object SnapDependencies {
  val sbtVersion = "0.12.1"

  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion % "provided"

  val webjarsPlay         = "org.webjars" % "webjars-play" % "2.0"
  val webjarsBootstrap    = "org.webjars" % "bootstrap" % "2.2.1"
  val commonsIo           = "commons-io" % "commons-io" % "2.0.1"


  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }
}
