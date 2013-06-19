import sbt._
import PlayProject._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object ActivatorBuild {
  val gitHeadCommit = SettingKey[Option[String]]("git-head-commit")

  def baseVersions: Seq[Setting[_]] = Seq(
    gitHeadCommit <<= (baseDirectory) apply { bd =>
      jgit(bd).headCommit
    },
    version <<= gitHeadCommit apply { commitOpt =>
      // TODO - Check to see if there were local file changes, and adapt timestamp appropriately...
      val df = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
      df setTimeZone java.util.TimeZone.getTimeZone("GMT")

      val extra = commitOpt getOrElse (df format (new java.util.Date))
      val default = "1.0-" + extra
      Option(sys.props("activator.version")) getOrElse default
    }
  )

  def formatPrefs = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(IndentSpaces, 2)
  }

  val typesafeIvyReleases = Resolver.url("typesafe-ivy-private-releases", new URL("http://private-repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.ivyStylePatterns)
  // TODO - When SBT 0.13 is out we won't need this...
  val typesafeIvySnapshots = Resolver.url("typesafe-ivy-private-snapshots", new URL("http://private-repo.typesafe.com/typesafe/ivy-snapshots/"))(Resolver.ivyStylePatterns)

  private val fixWhitespace = TaskKey[Seq[File]]("fix-whitespace")

  private def makeFixWhitespace(config: Configuration): Setting[_] = {
    fixWhitespace in config <<= (unmanagedSources in config, streams) map { (sources, streams) =>
      for (s <- sources) {
        Fixer.fixWhitespace(s, streams.log)
      }
      sources
    }
  }

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
      resolvers += typesafeIvySnapshots,
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
      ScalariformKeys.preferences in Test    := formatPrefs,
      makeFixWhitespace(Compile),
      makeFixWhitespace(Test),
      compileInputs in (Compile, compile) <<= (compileInputs in (Compile, compile)) dependsOn (fixWhitespace in Compile),
      compileInputs in Test <<= (compileInputs in Test) dependsOn (fixWhitespace in Test)
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
