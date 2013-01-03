import sbt._
import Keys._
import SbtSupport.sbtLaunchJar
import Packaging.{
  localRepoArtifacts,
  localRepoCreated,
  makeLocalRepoSettings,
  localRepoProjectsPublished,
  repackagedLaunchJar,
  localTemplateCacheCreated
}
import xsbt.api.Discovery
  import com.typesafe.packager.PackagerPlugin.Universal

object integration {
  
  val mains = TaskKey[Seq[String]]("integration-test-mains", "Discovered integration test main classes")
  val itContext = TaskKey[IntegrationContext]("integration-test-context")
  val tests = TaskKey[Unit]("integration-tests", "Runs all integration tests")

  val snapHome = TaskKey[File]("integration-snap-home", "Creates the snap-home for use in integration tests.")
  
  def settings: Seq[Setting[_]] = makeLocalRepoSettings ++ Seq(
    localRepoArtifacts := Seq.empty,
    // Make sure we publish this project.
    localRepoProjectsPublished <<= publishLocal,
    mains <<= compile in Compile map { a =>
      val defs = a.apis.internal.values.flatMap(_.api.definitions)
      val results = Discovery(Set("xsbti.Main"), Set())(defs.toSeq)
      results collect { 
        case (df, di) if !di.isModule => df.name
      }
    },
    itContext <<= (sbtLaunchJar, localRepoCreated, streams, version, target, scalaVersion, snapHome) map IntegrationContext.apply,
    tests <<= (itContext, mains) map { (ctx, ms) =>
      ms foreach ctx.runTest
    },
    localRepoArtifacts <+= (Keys.projectID, Keys.scalaBinaryVersion, Keys.scalaVersion) apply {
      (id, sbv, sv) => CrossVersion(sbv,sv)(id)
    },
    snapHome <<= (target, mappings in Universal in TheSnapBuild.dist) map { (t, m) =>
       val home = t / "snap-home"
       IO createDirectory home
       val homeFiles = for {
         (file, name) <- m
       } yield file -> (home / name)
       IO.copy(homeFiles)
       home
    }
  )
}


case class IntegrationContext(launchJar: File, 
                               repository: File,
                               streams: TaskStreams,
                               version: String,
                               target: File,
                               scalaVersion: String,
                               snapHome: File) {
  def runTest(name: String): Unit = {
    streams.log.info(" [IT] Running: " + name + " [IT]")
    val friendlyName = name replaceAll("\\.", "-")
    val cwd = target / "integration-test" / friendlyName
    IO createDirectory cwd
    val result = setup(name, cwd) ! streams.log match {
      case 0 => "SUCCESS"
      case n => "FAILURE" 
    }
    streams.log.info(" [IT] " + name + " result: " + result + " [IT]")
    if(result == "FAILURE") sys.error("Integration test failed")
  }
  
  
  
  private def setup(name: String, cwd: File): ProcessBuilder = {
    val props = cwd / "sbt.boot.properties"
    IO.write(props, makePropertiesString(name, cwd))
    IO createDirectory (cwd / "project")
    IO.write(cwd / "project" / "build.properties", "sbt.version=" + SnapDependencies.sbtVersion)
    val boot = cwd / "boot"
    Process(Seq("java", 
        "-Dsbt.boot.properties=" + props.getAbsolutePath, 
        "-Dsbt.boot.directory=" + boot.getAbsolutePath, 
        "-Dsnap.home=" +snapHome.getAbsolutePath,
        "-jar", 
        launchJar.getAbsolutePath), cwd)
  }
  
  
  private def makePropertiesString(name: String, cwd: File): String =
    """|[scala]
       |  version: %s
       |
       |[app]
       |  org: com.typesafe.snap
       |  name: snap-integration-tests
       |  version: %s
       |  class: %s
       |  cross-versioned: false
       |  components: xsbti
       |
       |[repositories]
       |  snap-local: file://${snap.local.repository-${snap.home-${user.home}/.snap}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
       |  snap-it-local: file://%s, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
       |
       |[boot]
       |  directory: ${sbt.boot.directory}
       |
       |[ivy]
       |  ivy-home: %s/.ivy2
       |  checksums: ${sbt.checksums-sha1,md5}
       |  override-build-repos: ${sbt.override.build.repos-false}
       |""".stripMargin format (scalaVersion, version, name, repository, cwd.getAbsolutePath, cwd.getAbsolutePath)
}
