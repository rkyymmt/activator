import sbt._
import PlayProject._
import Keys._

object SnapDependencies {
  val sbtVersion = "0.12.2"
  val scalaVersion = "2.10.0"
  val sbtSnapshotVersion = "0.13.0-20130313-052159"

  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion
  val sbtMain              = "org.scala-sbt" % "main" % sbtVersion
  val sbtTheSbt            = "org.scala-sbt" % "sbt" % sbtVersion
  val sbtIo                = "org.scala-sbt" % "io" % sbtVersion
  val sbtLogging           = "org.scala-sbt" % "logging" % sbtVersion
  val sbtProcess           = "org.scala-sbt" % "process" % sbtVersion
  
  // TODO - Don't use a snapshot version for this...
  val sbtCompletion           = "org.scala-sbt" % "completion" % sbtSnapshotVersion
  
  val akkaActor            = "com.typesafe.akka" % "akka-actor_2.10" % "2.1.0"
  
  val commonsIo            = "commons-io" % "commons-io" % "2.0.1"

  val mimeUtil             = "eu.medsea.mimeutil" % "mime-util" % "2.1.1"
  // need to manually set this to override an incompatible old version
  val slf4jLog4j           = "org.slf4j" % "slf4j-log4j12" % "1.6.6"

  val junitInterface       = "com.novocode" % "junit-interface" % "0.7"
  val specs2               = "org.specs2" % "specs2_2.10" % "1.13"


  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }

  // compile classpath and classes directory, with provided/optional or scala dependencies
  // specifically for projects that need remote-probe dependencies
  val requiredClasspath = TaskKey[Classpath]("required-classpath")

  def requiredJars: Setting[_] = {
    import xsbti.ArtifactInfo._
    requiredClasspath <<= (update, classDirectory in Compile) map { (report, classesDir) =>
      val jars = report.matching(configurationFilter(name = "compile") -- moduleFilter(organization = ScalaOrganization, name = ScalaLibraryID))
      val classes = classesDir.getAbsoluteFile
      (classes +: jars).classpath
    }
  }
}
