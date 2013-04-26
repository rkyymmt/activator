import sbt._
import PlayProject._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object ActivatorBuild {

  def baseVersions: Seq[Setting[_]] = Seq(
    version := {
      // TODO - We don't want to have to run "reload" for new versions....
      val df = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
      df setTimeZone java.util.TimeZone.getTimeZone("GMT")
      // TODO - Add git sha perhaps, because that might help with staleness...
      val default = "1.0-" + (df format (new java.util.Date))
      // TODO - Alternative way to release is desired....
      // TODO - Should also track a binary ABI value...
      Option(sys.props("activator.version")) getOrElse default
    }
  )

  def formatPrefs = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(IndentSpaces, 2)
  }

  val typesafeIvyReleases = Resolver.url("typesafe-ivy-releases", new URL("http://private-repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.ivyStylePatterns)

  def activatorDefaults: Seq[Setting[_]] =
    SbtScalariform.scalariformSettings ++
    Seq(
      organization := "com.typesafe.activator",
      version <<= version in ThisBuild,
      crossPaths := false,
      resolvers += "typesafe-mvn-releases" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers += Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns),
      // TODO - This won't be needed when SBT 0.13 is released...
      resolvers += typesafeIvyReleases,
      // TODO - Publish to ivy for sbt plugins, maven central otherwise?
      publishTo := Some(typesafeIvyReleases),
      publishMavenStyle := false,
      scalacOptions <<= (scalaVersion) map { sv =>
        Seq("-unchecked", "-deprecation") ++
          { if (sv.startsWith("2.9")) Seq.empty else Seq("-feature") }
      },
      javacOptions in Compile := Seq("-target", "1.6", "-source", "1.6"),
      javacOptions in (Compile, doc) := Seq("-source", "1.6"),
      libraryDependencies += Dependencies.junitInterface % "test",
      scalaVersion := Dependencies.scalaVersion,
      scalaBinaryVersion := "2.10",
      ScalariformKeys.preferences in Compile := formatPrefs,
      ScalariformKeys.preferences in Test    := formatPrefs
    )

  def sbtShimPluginSettings: Seq[Setting[_]] =
    activatorDefaults ++
    Seq(
      scalaVersion := Dependencies.sbtPluginScalaVersion,
      scalaBinaryVersion := Dependencies.sbtPluginScalaVersion,
      sbtPlugin := true,
      publishMavenStyle := false
    )

  def ActivatorProject(name: String): Project = (
    Project("activator-" + name, file(name))
    settings(activatorDefaults:_*)
  )


  def SbtChildProject(name: String): Project = (
    Project("sbt-child-" + name, file("sbt-child") / name)
    settings(activatorDefaults:_*)
  )

  def SbtShimPlugin(name: String): Project = (
    Project("sbt-shim-" + name, file("sbt-shim") / name)
    settings(sbtShimPluginSettings:_*)
  )

  def ActivatorPlayProject(name: String): Project = (
    play.Project("activator-" + name, path = file(name))
    settings(activatorDefaults:_*)
  )

  def ActivatorJavaProject(name: String): Project = (
    Project("activator-" + name, file(name))
    settings(activatorDefaults:_*)
    settings(
        autoScalaLibrary := false
    )
  )
}
