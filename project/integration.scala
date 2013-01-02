import sbt._
import Keys._
import SbtSupport.sbtLaunchJar
import Packaging.{
  localRepoArtifacts,
  localRepoCreated,
  makeLocalRepoSettings,
  localRepoProjectsPublished
}
import xsbt.api.Discovery

object integration {
  
  val mains = TaskKey[Seq[String]]("integration-test-mains", "Discovered integration test main classes")
  val itContext = TaskKey[IntegrationContext]("integration-test-context")
  val tests = TaskKey[Unit]("integration-tests", "Runs all integration tests")
  
  def settings: Seq[Setting[_]] = makeLocalRepoSettings ++ Seq(
    localRepoArtifacts <<= localRepoArtifacts in TheSnapBuild.dist,
    // Make sure we publish this project.
    localRepoProjectsPublished <<= (publishLocal, localRepoProjectsPublished) map ((_, v) => v),
    mains <<= compile in Compile map { a =>
      val defs = a.apis.internal.values.flatMap(_.api.definitions)
      val results = Discovery(Set("xsbti.Main"), Set())(defs.toSeq)
      results collect { 
        case (df, di) if !di.isModule => df.name
      }
    },
    itContext <<= (sbtLaunchJar, localRepoCreated, streams, version, target, scalaVersion) map IntegrationContext.apply,
    tests <<= (itContext, mains) map { (ctx, ms) =>
      ms foreach ctx.runTest
    },
    localRepoArtifacts <+= (Keys.projectID, Keys.scalaBinaryVersion, Keys.scalaVersion) apply {
      (id, sbv, sv) => CrossVersion(sbv,sv)(id)
    }
  )
}


case class IntegrationContext(launchJar: File, repository: File, streams: TaskStreams, version: String, target: File, scalaVersion: String) {
  def runTest(name: String): Unit = {
    streams.log.info(" [IT] Running: " + name + " [IT]")
    val friendlyName = name replaceAll("\\.", "-")
    val cwd = target / friendlyName
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
    
    Process(Seq("java", 
        "-Dsbt.boot.properties=" + props.getAbsolutePath, 
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
       |  snap-local: file://%s, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
       |
       |[boot]
       |  directory: %s
       |
       |[ivy]
       |  ivy-home: %s/.ivy2
       |  checksums: ${sbt.checksums-sha1,md5}
       |  override-build-repos: ${sbt.override.build.repos-false}
       |""".stripMargin format (scalaVersion, version, name, repository, cwd.getAbsolutePath, cwd.getAbsolutePath)
}