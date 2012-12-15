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
      resolvers += Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns) 
    )

  def SnapProject(name: String): Project = (
    Project("snap-" + name, file(name))
    settings(snapDefaults:_*)
  )

  def SnapPlayProject(name: String): Project = (
    play.Project("snap-" + name, path = file(name)) 
    settings(snapDefaults:_*)
    settings(scalaBinaryVersion := "2.10")
  )

  def SnapJavaProject(name: String): Project = (
    Project("snap-" + name, file(name))
    settings(snapDefaults:_*)
    settings(
        autoScalaLibrary := false
    )
  )
}

