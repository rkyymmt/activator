import sbt._
import SnapBuild._
import SnapDependencies._
import Packaging.localRepoArtifacts
// NOTE - This file is only used for SBT 0.12.x, in 0.13.x we'll use build.sbt and scala libraries.
// As such try to avoid putting stuff in here so we can see how good build.sbt is without build.scala.


object TheSnapBuild extends Build {

  // ADD sbt launcher support here.
  override def settings = super.settings ++ SbtSupport.buildSettings

  val root = (
    Project("root", file("."))  // TODO - Oddities with clean..
    aggregate(ui, launcher, dist)
  )

  lazy val props = (
    SnapJavaProject("props")
    settings(Properties.makePropertyClassSetting(SnapDependencies.sbtVersion):_*)
  )

  // Theser are the projects we want in the local SNAP repository
  lazy val publishedProjects = Seq(ui, launcher)

  lazy val ui = (
    SnapPlayProject("ui")
    dependsOnRemote(
      webjarsPlay,
      webjarsBootstrap,
      commonsIo,
      sbtLauncherInterface
    )
    dependsOn(props)
  )

  // TODO - SBT plugin, or just SBT integration?

  lazy val launcher = (
    SnapProject("launcher")
    dependsOnRemote(sbtLauncherInterface)
    settings(
      Keys.scalaBinaryVersion <<= Keys.scalaVersion
    )
    dependsOn(props)
  )

  lazy val dist = (
    SnapProject("dist")
    settings(Packaging.settings:_*)
    settings(
      Keys.scalaBinaryVersion <<= Keys.scalaVersion,
      Keys.resolvers ++= Seq(
        "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns),
        Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
      ),
      // TODO - Do this better - This is where we define what goes in the local repo cache.

      localRepoArtifacts <++= (publishedProjects map { ref =>
        // The annoyance caused by cross-versioning.
        (Keys.projectID in ref, Keys.scalaBinaryVersion in ref, Keys.scalaVersion in ref) apply {
          (id, sbv, sv) =>
            CrossVersion(sbv,sv)(id)
        }
      }).join,
      localRepoArtifacts += "org.scala-sbt" % "sbt" % "0.12.1",
      localRepoArtifacts ++= {
        val sbt = "0.12"
        val scala = "2.9.2"
        Seq(
          Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-site" % "0.6.0", sbt, scala),
          Defaults.sbtPluginExtra("com.typesafe" % "sbt-native-packager" % "0.4.3", sbt, scala),
          Defaults.sbtPluginExtra("play" % "sbt-plugin" % "2.1-RC1", sbt, scala),
          Defaults.sbtPluginExtra("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.0", sbt, scala),
          Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-pgp" % "0.7", sbt, scala)
        )
      }
    )
  )
}
