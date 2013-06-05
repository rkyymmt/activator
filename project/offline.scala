import sbt._
import Keys._

import SbtSupport.sbtLaunchJar
import LocalTemplateRepo.localTemplateCacheCreated
import Packaging.localRepoCreated

object offline {
  
  val runOfflineTests = TaskKey[Unit]("offline-tests", "runs tests to ensure templates can work with the offline repository.")
  
  // set up offline repo tests as integration tests.
  def settings: Seq[Setting[_]] = Seq(
    runOfflineTests <<= (localTemplateCacheCreated in TheActivatorBuild.localTemplateRepo,
                         localRepoCreated in TheActivatorBuild.dist,
                         sbtLaunchJar, 
                         streams) map offlineTestsTask,
    integration.tests <<= runOfflineTests
  )
  
  def offlineTestsTask(templateRepo: File, localIvyRepo: File, launcher: File, streams: TaskStreams): Unit = {
    // TODO - Use a target directory instead of temporary.
    IO.withTemporaryDirectory { dir =>
      IO.copyDirectory(templateRepo, dir)
      runofflinetests(dir, localIvyRepo, launcher, streams.log)
    }
  }
  
  def runofflinetests(templateRepo: File, localIvyRepo: File, launcher: File, log: sbt.Logger): Unit = {
    val results = 
      for {
        project <- findTestDirs(templateRepo)
        name = "[" + project.getName + "]"
        // TODO - Log here.
        result = runTest(localIvyRepo, project, launcher, log) 
      } yield name -> result
    // TODO - Recap failrues!  
    if(results exists (_._2 != true)) {
      val fcount = results.filterNot(_._2).length
      log.info("[OFFLINETEST] " + fcount + " failures in " + results.length + " tests...")
      for((name, result) <- results) {
        log.info(" [OFFLINETEST] "+name+" - " + (if(result) "SUCCESS" else "FAILURE"))
      }
      sys.error("Tests were unsucessful")
    }
    ()
  }
  
  
  def findTestDirs(root: File): Seq[File] = {
    for {
      dir <- (root.***).get
      if (dir / "project/build.properties").exists
    } yield dir
  }
  
  def runTest(localIvyRepo: File, template: File, launcher: File, log: sbt.Logger): Boolean = {
    sbt.IO.withTemporaryFile("sbt", "repositories") { repoFile =>
      makeRepoFile(repoFile, localIvyRepo)
      def sbt(args: String*) = runSbt(launcher, repoFile, template, log)(args)
      sbt("update")
    }
  }
  
  def runSbt(launcher: File, repoFile: File, cwd: File, log: sbt.Logger)(args: Seq[String]): Boolean = 
    IO.withTemporaryDirectory { globalBase =>
      val jvmargs = Seq(
        "-Dsbt.repository.config="+repoFile.getCanonicalPath,
        "-Dsbt.override.build.repos=true",
        // TODO - Enough for fresh cache?
        "-Dsbt.ivy.home="+(globalBase / ".ivy2").getAbsolutePath,
        "-Dsbt.version="+Dependencies.sbtVersion,
        "-Dsbt.global.base="+globalBase.getAbsolutePath
      )
      val cmd = Seq("java") ++ jvmargs ++ Seq("-jar", launcher.getCanonicalPath) ++ args
      Process(cmd, cwd) ! log match {
        case 0 => true
        case n => false
      }
    }
  
  def makeRepoFile(props: File, localIvyRepo: File): Unit = {
    // TODO - Don't hardcode the props file!
    IO.write(props,
"""
[repositories]
  activator-local: file://%s, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
""" format(localIvyRepo.getCanonicalPath))
  }
}