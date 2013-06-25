import sbt._
import ActivatorBuild._
import Dependencies._
import Packaging.localRepoArtifacts
import com.typesafe.sbt.S3Plugin._
import com.typesafe.sbt.SbtNativePackager.Universal
// NOTE - This file is only used for SBT 0.12.x, in 0.13.x we'll use build.sbt and scala libraries.
// As such try to avoid putting stuff in here so we can see how good build.sbt is without build.scala.


object TheActivatorBuild extends Build {

  // ADD sbt launcher support here.
  override def settings = super.settings ++ SbtSupport.buildSettings ++ baseVersions ++ Seq(
    // This is a hack, so the play application will have the right view of the template directory.
    Keys.baseDirectory <<= Keys.baseDirectory apply { bd =>
      sys.props("activator.home") = bd.getAbsoluteFile.getAbsolutePath  // TODO - Make space friendly
      bd
    }
  ) ++ play.Project.intellijCommandSettings(play.Project.SCALA) // workaround for #24

  val root = (
    Project("root", file("."))  // TODO - Oddities with clean..
    aggregate((publishedProjects.map(_.project) ++ 
              Seq(dist.project, it.project, localTemplateRepo.project, offlinetests.project)):_*)
    settings(
      // Stub out commands we run frequently but don't want them to really do anything.
      Keys.publish := {},
      Keys.publishLocal := {}
    )
  )
  
  lazy val news: Project = (
    Project("news", file("news"))
    settings(NewsHelper.settings:_*)
  )
  
  // This project helps us isolate creating the local template repository for testing.
  lazy val localTemplateRepo: Project = (
    Project("template-repository", file("template-repository"))
    settings(LocalTemplateRepo.settings:_*)
    settings(Keys.publishLocal := {},
             Keys.publish := {},
             Keys.resolvers += typesafeIvyReleases)
  )

  // These are the projects we want in the local repository we deploy.
  lazy val publishedProjects: Seq[Project] = Seq(ui, uiCommon, launcher, props)

  // basic project that gives us properties to use in other projects.
  lazy val props = (
    ActivatorJavaProject("props")
    settings(Properties.makePropertyClassSetting(Dependencies.sbtVersion, Dependencies.scalaVersion):_*)
  )

  // Helper for UI projects (CLI + GUI)
  lazy val uiCommon = (
    ActivatorProject("ui-common")
    dependsOnRemote(templateCache)
    dependsOn(props)
  )

  val verboseSbtTests = false

  /*def configureSbtTest(testKey: Scoped) = Seq(
    // set up embedded sbt for tests, we fork so we can set
    // system properties.
    Keys.fork in testKey := true,
    Keys.javaOptions in testKey <<= (
      SbtSupport.sbtLaunchJar,
      Keys.javaOptions in testKey,
      requiredClasspath in sbtRemoteController,
      Keys.compile in Compile in sbtRemoteController) map {
      (launcher, oldOptions, probeCp, _) =>
        oldOptions ++ Seq("-Dactivator.sbt.no-shims=true",
                          "-Dactivator.sbt.launch.jar=" + launcher.getAbsoluteFile.getAbsolutePath,
                          "-Dactivator.remote.controller.classpath=" + Path.makeString(probeCp.files)) ++
      (if (verboseSbtTests)
        Seq("-Dakka.loglevel=DEBUG",
            "-Dakka.actor.debug.autoreceive=on",
            "-Dakka.actor.debug.receive=on",
            "-Dakka.actor.debug.lifecycle=on")
       else
         Seq.empty)
    })*/

  
  // We use this to ensure all necessary shims are pubished before we run, so sbt can resolve them.
  lazy val shimsPublished = TaskKey[Unit]("sbt-shims-published")
  lazy val ui = (
    ActivatorPlayProject("ui")
    dependsOnRemote(
      commonsIo, mimeUtil, slf4jLog4j,
      sbtLauncherInterface % "provided",
      sbtrcParent, 
      sbtrcParent % "test->test"
    )
    dependsOn(props, uiCommon)
    settings(play.Project.playDefaultPort := 8888)
    // set up debug props for forked tests
    //settings(configureSbtTest(Keys.test): _*)
    //settings(configureSbtTest(Keys.testOnly): _*)
    // set up debug props for "run"
    settings(
      // Here we hack so that we can see the sbt-rc classes...  
      Keys.update <<= (
          SbtSupport.sbtLaunchJar,
          Keys.update,
          //requiredClasspath in sbtRemoteController,
          //Keys.compile in Compile in sbtRemoteController,
          LocalTemplateRepo.localTemplateCacheCreated in localTemplateRepo) map {
        (launcher, update, /*probeCp, _, _,*/ templateCache) =>
          // We register the location after it's resolved so we have it for running play...
          sys.props("activator.sbt.launch.jar") = launcher.getAbsoluteFile.getAbsolutePath
          //sys.props("activator.remote.controller.classpath") = Path.makeString(probeCp.files)
          sys.props("activator.template.cache") = templateCache.getAbsolutePath
          sys.props("activator.runinsbt") = "true"
          System.err.println("Updating sbt launch jar: " + sys.props("activator.sbt.launch.jar"))
          //System.err.println("Remote probe classpath = " + sys.props("activator.remote.controller.classpath"))
          System.err.println("Template cache = " + sys.props("activator.template.cache"))
          update
      }
    )
    settings(
      Keys.compile in Compile <<= (Keys.compile in Compile, Keys.baseDirectory, Keys.streams) map { (oldCompile, baseDir, streams) =>
        val jsErrors = JsChecker.fixAndCheckAll(baseDir, streams.log)
        for (error <- jsErrors) {
          streams.log.error(error)
        }
        if (jsErrors.nonEmpty)
          throw new RuntimeException(jsErrors.length + " JavaScript formatting errors found")
        else
          streams.log.info("JavaScript whitespace meets our exacting standards")
        oldCompile
      }
    )
  )

  lazy val launcher = (
    ActivatorProject("launcher")
    dependsOnRemote(sbtLauncherInterface, sbtCompletion)
    dependsOn(props, uiCommon)
  )

  // A hack project just for convenient IvySBT when resolving artifacts into new local repositories.
  lazy val dontusemeresolvers = (
    ActivatorProject("dontuseme")
    settings(
      // This hack removes the project resolver so we don't resolve stub artifacts.
      Keys.fullResolvers <<= (Keys.externalResolvers, Keys.sbtResolver) map (_ :+ _),
      Keys.resolvers += Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
      Keys.publish := {},
      Keys.publishLocal := {}
    )
  )
  lazy val it = (
      ActivatorProject("integration-tests")
      settings(integration.settings:_*)
      dependsOnRemote(sbtLauncherInterface, sbtIo210, sbtrcParent)
      dependsOn(props)
      settings(
        com.typesafe.sbtidea.SbtIdeaPlugin.ideaIgnoreModule := true,
        Keys.publish := {}
      )
  )

  lazy val offlinetests = (
    ActivatorProject("offline-tests")
    settings(
      Keys.publish := {},
      Keys.publishLocal := {}
    )
    settings(offline.settings:_*)
  )
  
  lazy val dist = (
    ActivatorProject("dist")
    settings(Packaging.settings:_*)
    settings(s3Settings:_*)
    settings(
      // TODO - Should publish be pushing the S3 upload?
      Keys.publish := {},
      Keys.publishLocal := {},
      Keys.scalaBinaryVersion <<= Keys.scalaVersion,
      Keys.resolvers ++= Seq(
        "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns),
        Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
      ),
      // TODO - Do this better - This is where we define what goes in the local repo cache.
      localRepoArtifacts <++= (publishedProjects.toSeq map { ref =>
        (Keys.projectID in ref) apply { id => id }
      }).join,
      localRepoArtifacts ++=
        Seq("org.scala-sbt" % "sbt" % Dependencies.sbtVersion,
            // For some reason, these are not resolving transitively correctly!
            "org.scala-lang" % "scala-compiler" % Dependencies.sbtPluginScalaVersion,
            "org.scala-lang" % "scala-compiler" % Dependencies.scalaVersion,
            // TODO - Why do we have to specify these?
            jna,
            jline,
            jsch,
            commonsCodec,
            commonsHttpClient,
            guava,
            xmlApis,
            // USED BY templates. TODO - autofind these
            playJava,
            scalatest,
            webjars,
            webjarsBootstrap,
            //"org.webjars" % "bootstrap" % "2.1.1",
            webjarsFlot,
            webjarsPlay,
            // WTF ANORM?
            "org.avaje.ebeanorm" % "avaje-ebeanorm" % "3.2.1",
            "org.avaje.ebeanorm" % "avaje-ebeanorm" % "3.1.2",
            "org.avaje.ebeanorm" % "avaje-ebeanorm" % "3.1.1",
            "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.1.1",
            "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "3.2.1",
            
            "junit" % "junit" % "3.8.1",
            "junit" % "junit-dep" % "4.8.2",
            "junit" % "junit" % "4.11",
            "com.novocode" % "junit-interface" % "0.7",
            
            // Hipster akka required for the Java API.
            // Remove when we consolidate akka versions.
            "com.typesafe.akka" % "akka-actor_2.10" % "2.2-M3",
            "com.typesafe.akka" % "akka-testkit_2.10" % "2.2-M3",
            // Regular akka for normal folks
            akkaActor,
            akkaSlf4j,
            akkaTestkit,
            sbtrcParent,
            sbtrcController,
            sbtshimDefaults,
            sbtshimPlay,
            sbtshimEclipse,
            sbtshimIdea
        ),
      localRepoArtifacts ++=  Seq(
        playSbtPlugin,
        eclipseSbtPlugin,
        ideaSbtPlugin,
        pgpPlugin
      ),
      Keys.mappings in S3.upload <<= (Keys.packageBin in Universal, Keys.version) map { (zip, v) =>
        Seq(zip -> ("typesafe-activator/%s/typesafe-activator-%s.zip" format (v, v)))
      },
      S3.host in S3.upload := "downloads.typesafe.com.s3.amazonaws.com",
      S3.progress in S3.upload := true
    )
  )
}
