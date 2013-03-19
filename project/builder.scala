import sbt._
import PlayProject._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object BuilderBuild {

  def baseVersions: Seq[Setting[_]] = Seq(
    version := {
      // TODO - We don't want to have to run "reload" for new versions....
      val df = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
      df setTimeZone java.util.TimeZone.getTimeZone("GMT")
      // TODO - Add git sha perhaps, because that might help with staleness...
      val default = "1.0-" + (df format (new java.util.Date))
      // TODO - Alternative way to release is desired....
      // TODO - Should also track a binary ABI value...
      Option(sys.props("builder.version")) getOrElse default
    }
  )

  def formatPrefs = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(IndentSpaces, 2)
  }

  def builderDefaults: Seq[Setting[_]] =
    SbtScalariform.scalariformSettings ++
    Seq(
      organization := "com.typesafe.builder",
      version <<= version in ThisBuild,
      crossPaths := false,
      resolvers += "typesafe-mvn-releases" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers += Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns),
      // TODO - This won't be needed when SBT 0.13 is released...
      resolvers += Resolver.url("typesafe-ivy-releases", new URL("http://private-repo.typesafe.com/typesafe/ivy-snapshots/"))(Resolver.ivyStylePatterns),
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


  def BuilderProject(name: String): Project = (
    Project("builder-" + name, file(name))
    settings(builderDefaults:_*)
  )

  
  def SbtChildProject(name: String): Project = (
    Project("sbt-child-" + name, file("sbt-child") / name)
    settings(builderDefaults:_*)
  )
  
  def BuilderPlayProject(name: String): Project = (
    play.Project("builder-" + name, path = file(name)) 
    settings(builderDefaults:_*)
  )

  def BuilderJavaProject(name: String): Project = (
    Project("builder-" + name, file(name))
    settings(builderDefaults:_*)
    settings(
        autoScalaLibrary := false
    )
  )
}

