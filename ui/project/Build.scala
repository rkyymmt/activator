import sbt._
import Keys._
import PlayProject._
import sbt.Resolver

object ApplicationBuild extends Build {

    val appName         = "snap"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "org.webjars" % "webjars-play" % "2.0",
      "org.webjars" % "bootstrap" % "2.2.1",
      "commons-io" % "commons-io" % "2.0.1"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += Resolver.mavenLocal
    )

}
