import sbt._
import Keys._
import PlayProject._
import sbt.Resolver

object ApplicationBuild extends Build {

    val appName         = "launcher"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "org.webjars" % "bootstrap" % "2.2.1"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += Resolver.mavenLocal
    )

}
