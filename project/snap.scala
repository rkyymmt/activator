import sbt._
import PlayProject._
import Keys._

object SnapBuild {

  def snapDefaults: Seq[Setting[_]] =
    Seq(
      organization := "com.typesafe.snap",
      version := "1.0-SNAPSHOT"
    )

  def SnapProject(name: String): Project = (
    Project("snap-" + name, file(name))
    settings(snapDefaults:_*)
  )

  def SnapPlayProject(name: String): Project = (
    play.Project("snap-" + name, "ignore-me-version", Seq.empty) 
    settings(snapDefaults:_*)
  )
}

