import sbt._
import PlayProject._
import Keys._

object SnapBuild {

  def baseVersions: Seq[Setting[_]] = Seq(
    version := {
      // TODO - We don't want to have to run "reload" for new versions....
      val df = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
      df setTimeZone java.util.TimeZone.getTimeZone("GMT")
      // TODO - Add git sha perhaps, because that might help with staleness...
      "1.0-" + (df format (new java.util.Date))
    }
  )
  
  def snapDefaults: Seq[Setting[_]] =
    Seq(
      organization := "com.typesafe.snap",
      version <<= version in ThisBuild,
      crossPaths := false,
      resolvers += "typesafe-mvn-releases" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers += Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns),
      scalacOptions <<= (scalaVersion) map { sv =>
        Seq("-unchecked", "-deprecation") ++
          { if (sv.startsWith("2.9")) Seq.empty else Seq("-feature") }
      },
      javacOptions in Compile := Seq("-target", "1.6", "-source", "1.6"),
      javacOptions in (Compile, doc) := Seq("-source", "1.6"),
      libraryDependencies += SnapDependencies.junitInterface % "test",
      scalaVersion := SnapDependencies.scalaVersion,
      scalaBinaryVersion := "2.10"
    )


  def SnapProject(name: String): Project = (
    Project("snap-" + name, file(name))
    settings(snapDefaults:_*)
  )

  
  def SbtChildProject(name: String): Project = (
    Project("sbt-child-" + name, file("sbt-child") / name)
    settings(snapDefaults:_*)
  )
  
  def SnapPlayProject(name: String): Project = (
    play.Project("snap-" + name, path = file(name)) 
    settings(snapDefaults:_*)
  )

  def SnapJavaProject(name: String): Project = (
    Project("snap-" + name, file(name))
    settings(snapDefaults:_*)
    settings(
        autoScalaLibrary := false
    )
  )
}

