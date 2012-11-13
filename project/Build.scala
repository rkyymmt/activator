import sbt._
import sbt.Keys._

object LauncherBuild extends Build {
  lazy val root = Project(
    "launcher",
    file("."),
    settings = Defaults.defaultSettings ++ Seq(
      organization := "com.typesafe",
      name := "launcher",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.9.2",
      libraryDependencies <+= scalaVersion { "org.scala-lang" % "scala-swing" % _ }
    )
  )
}
