import sbt._
import PlayProject._
import Keys._

object SnapDependencies {
  val sbtVersion = "0.12.2-RC1"

  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion
  val sbtMain              = "org.scala-sbt" % "main" % sbtVersion
  val sbtTheSbt            = "org.scala-sbt" % "sbt" % sbtVersion
  val sbtIo                = "org.scala-sbt" % "io" % sbtVersion
  val sbtLogging           = "org.scala-sbt" % "logging" % sbtVersion
  val sbtProcess           = "org.scala-sbt" % "process" % sbtVersion
  
  val akkaActor            = "com.typesafe.akka" % "akka-actor_2.10" % "2.1.0"
  
  val webjarsPlay          = "org.webjars" % "webjars-play" % "2.0"
  val webjarsBootstrap     = "org.webjars" % "bootstrap" % "2.2.1"
  val commonsIo            = "commons-io" % "commons-io" % "2.0.1"

  val junitInterface       = "com.novocode" % "junit-interface" % "0.7"


  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }
}
