import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "reactive-stock-tweets"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "org.webjars" % "webjars-play" % "2.1.0-1",
    "org.webjars" % "bootstrap" % "2.1.1",
    "org.webjars" % "flot" % "0.8.0"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
  )

}
