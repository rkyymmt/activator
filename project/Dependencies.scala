import sbt._
import PlayProject._
import Keys._

object Dependencies {
  val sbtVersion = "0.12.4-RC2"
  val sbtPluginVersion = "0.12"
  val sbtPluginScalaVersion = "2.9.2"
  val scalaVersion = "2.10.1"
  val sbtSnapshotVersion = "0.13.0-Beta2"
  val luceneVersion = "4.2.1"
  val templateCacheVersion = "1.0-af716b6259a60a51663e41ae079c5e6569df414b"
  val sbtRcVersion = "1.0-24a164e2e6cdef9fe36864fd185a2f36bdc6ce7a"
  val playVersion = "2.1.1"
  val akkaVersion = "2.1.2"

  val activatorCommon      = "com.typesafe.activator" % "activator-common" % templateCacheVersion
  val templateCache        = "com.typesafe.activator" % "activator-templates-cache" % templateCacheVersion


  val sbtIo210             = "org.scala-sbt" % "io" % sbtSnapshotVersion
  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion
  val sbtMain              = "org.scala-sbt" % "main" % sbtVersion
  val sbtTheSbt            = "org.scala-sbt" % "sbt" % sbtVersion
  val sbtIo                = "org.scala-sbt" % "io" % sbtVersion
  val sbtLogging           = "org.scala-sbt" % "logging" % sbtVersion
  val sbtProcess           = "org.scala-sbt" % "process" % sbtVersion
  
  
  // sbtrc projects
  val sbtrcParent          = "com.typesafe.sbtrc" % "sbt-rc-parent" % sbtRcVersion
  val sbtrcController      = "com.typesafe.sbtrc" % "sbt-rc-controller" % sbtRcVersion
  val sbtshimDefaults      =  Defaults.sbtPluginExtra("com.typesafe.sbtrc" % "sbt-shim-defaults" % sbtRcVersion, sbtPluginVersion, sbtPluginScalaVersion)
  val sbtshimPlay          =  Defaults.sbtPluginExtra("com.typesafe.sbtrc" % "sbt-shim-play" % sbtRcVersion, sbtPluginVersion, sbtPluginScalaVersion)
  val sbtshimEclipse       =  Defaults.sbtPluginExtra("com.typesafe.sbtrc" % "sbt-shim-eclipse" % sbtRcVersion, sbtPluginVersion, sbtPluginScalaVersion)
  val sbtshimIdea          =  Defaults.sbtPluginExtra("com.typesafe.sbtrc" % "sbt-shim-idea" % sbtRcVersion, sbtPluginVersion, sbtPluginScalaVersion)
  
  
  // TODO - Don't use a snapshot version for this...
  val sbtCompletion           = "org.scala-sbt" % "completion" % sbtSnapshotVersion
  
  val akkaActor            = "com.typesafe.akka" % "akka-actor_2.10" % akkaVersion
  val akkaSlf4j            = "com.typesafe.akka" % "akka-slf4j_2.10" % akkaVersion
  val akkaTestkit          = "com.typesafe.akka" % "akka-testkit_2.10" % akkaVersion
  
  val commonsIo            = "commons-io" % "commons-io" % "2.0.1"

  val mimeUtil             = "eu.medsea.mimeutil" % "mime-util" % "2.1.1"
  // need to manually set this to override an incompatible old version
  val slf4jLog4j           = "org.slf4j" % "slf4j-log4j12" % "1.6.6"

  val junitInterface       = "com.novocode" % "junit-interface" % "0.7"
  //val specs2               = "org.specs2" % "specs2_2.10" % "1.13"

  // SBT plugins we have to shim
  val playSbtPlugin        =  Defaults.sbtPluginExtra("play" % "sbt-plugin" % playVersion, sbtPluginVersion, sbtPluginScalaVersion)
  val eclipseSbtPlugin     =  Defaults.sbtPluginExtra("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.2.0", sbtPluginVersion, sbtPluginScalaVersion)
  val ideaSbtPlugin        =  Defaults.sbtPluginExtra("com.github.mpeltonen" % "sbt-idea" % "1.3.0", sbtPluginVersion, sbtPluginScalaVersion)
  val pgpPlugin            =  Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-pgp" % "0.8", sbtPluginVersion, sbtPluginScalaVersion)


  // Embedded databases / index
  val lucene = "org.apache.lucene" % "lucene-core" % luceneVersion
  val luceneAnalyzerCommon = "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion
  val luceneQueryParser = "org.apache.lucene" % "lucene-queryparser" % luceneVersion

  
  
  
  // Dependencies that only show up in the local repository, and aren't automatically resolved for some reason:
  val jna = "net.java.dev.jna" % "jna" % "3.2.3"
  val jline = "jline" % "jline" % "0.9.94"
  val jsch = "com.jcraft" % "jsch" % "0.1.44-1"
  val commonsCodec = "commons-codec" % "commons-codec" % "1.3"
  val commonsHttpClient = "org.apache.httpcomponents" % "httpclient" % "4.0.1"
  val guava = "com.google.guava" % "guava" % "11.0.2"
  val xmlApis = "xml-apis" % "xml-apis" % "1.0.b2"
  
  
  // Used in Templates
  val playJava = "play" % "play-java_2.10" % playVersion
  val scalatest = "org.scalatest" % "scalatest_2.10" % "1.9.1"
  val webjars = "org.webjars" % "webjars-play" % "2.1.0-1"
  val webjarsBootstrap = "org.webjars" % "bootstrap" % "2.3.1"
  val webjarsFlot = "org.webjars" % "flot" % "0.8.0"
  val webjarsPlay = "org.webjars" % "webjars-play" % "2.1.0"
  
  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  final class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }
  // DSL for adding source dependencies ot projects.
  def dependsOnSource(dir: String): Seq[Setting[_]] = {
    import Keys._
    Seq(unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, baseDirectory) { (srcDirs, base) => (base / dir / "src/main/scala") +: srcDirs },
        unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test, baseDirectory) { (srcDirs, base) => (base / dir / "src/test/scala") +: srcDirs })
  }
  implicit def p2source(p: Project): SourceDepHelper = new SourceDepHelper(p)
  final class SourceDepHelper(p: Project) {
    def dependsOnSource(dir: String): Project =
      p.settings(Dependencies.dependsOnSource(dir):_*)
  }
  
  // compile classpath and classes directory, with provided/optional or scala dependencies
  // specifically for projects that need remote-probe dependencies
  val requiredClasspath = TaskKey[Classpath]("required-classpath")

  def requiredJars(deps: ProjectReference*): Setting[_] = {
    import xsbti.ArtifactInfo._
    import Project.Initialize
    val dependentProjectClassPaths: Seq[Initialize[Task[Seq[File]]]] =
      (deps map { proj => 
        (classDirectory in Compile in proj) map { dir => Seq(dir) }
      })
    val ivyDeps: Initialize[Task[Seq[File]]] =  update map { report =>
      val jars = report.matching(configurationFilter(name = "compile") -- moduleFilter(organization = ScalaOrganization, name = ScalaLibraryID))
      jars
    }
    val localClasses: Initialize[Task[Seq[File]]] = (classDirectory in Compile) map { dir =>
      Seq(dir)
    }
    // JOin everyone
    def joinCp(inits: Seq[Initialize[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] =
      inits reduce { (lhs, rhs) =>
        (lhs zip rhs).flatMap { case (l,r) =>
          l.flatMap[Seq[File]] { files =>
            r.map[Seq[File]] { files2 =>
              files ++ files2
            }
          }
        }
      }
    requiredClasspath <<= joinCp(dependentProjectClassPaths ++ Seq(ivyDeps, localClasses)) map {
      _.classpath
    }
  }
}
