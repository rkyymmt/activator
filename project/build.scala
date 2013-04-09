import sbt._
import BuilderBuild._
import Dependencies._
import Packaging.localRepoArtifacts
import com.typesafe.sbt.S3Plugin._
import com.typesafe.sbt.SbtNativePackager.Universal
// NOTE - This file is only used for SBT 0.12.x, in 0.13.x we'll use build.sbt and scala libraries.
// As such try to avoid putting stuff in here so we can see how good build.sbt is without build.scala.


object TheBuilderBuild extends Build {

  // ADD sbt launcher support here.
  override def settings = super.settings ++ SbtSupport.buildSettings ++ baseVersions ++ Seq(
    // This is a hack, so the play application will have the right view of the template directory.
    Keys.baseDirectory <<= Keys.baseDirectory apply { bd =>
      sys.props("builder.home") = bd.getAbsoluteFile.getAbsolutePath
      bd
    }
  ) ++ play.Project.intellijCommandSettings(play.Project.SCALA) // workaround for #24

  val root = (
    Project("root", file("."))  // TODO - Oddities with clean..
    aggregate((publishedProjects.map(_.project) ++ Seq(dist.project, it.project)):_*)
    settings(
      // Stub out commands we run frequently but don't want them to really do anything.
      Keys.publish := {},
      Keys.publishLocal := {}
    )
  )

  // These are the projects we want in the local Builder repository
  lazy val publishedSbtShimProjects = Set(playShimPlugin, eclipseShimPlugin, ideaShimPlugin, sbtUiInterface, defaultsShimPlugin)
  lazy val publishedProjects = Seq(io, common, ui, launcher, props, cache, sbtRemoteProbe, sbtDriver) ++ publishedSbtShimProjects

  // basic project that gives us properties to use in other projects.
  lazy val props = (
    BuilderJavaProject("props")
    settings(Properties.makePropertyClassSetting(Dependencies.sbtVersion, Dependencies.scalaVersion):_*)
  )

  lazy val io = (
    BuilderProject("io")
    dependsOnRemote(junitInterface % "test", specs2 % "test")
  )

  lazy val common = (
    BuilderProject("common")
    dependsOnRemote(junitInterface % "test", specs2 % "test")
    dependsOn(io)
  )

  lazy val cache = (
    BuilderProject("cache")
    dependsOn(props, common)
    dependsOnRemote(junitInterface % "test")
  )
  
  lazy val sbtUiInterface = (
      SbtShimPlugin("ui-interface")
      settings(
          Keys.scalaVersion := Dependencies.sbtPluginScalaVersion, 
          Keys.scalaBinaryVersion <<= Keys.scalaVersion,
          Keys.crossVersion := CrossVersion.Disabled,
          Keys.projectID <<=  Keys.projectID apply { id =>
            id.copy(extraAttributes = Map.empty)
          })
      dependsOnRemote(
          sbtMain % "provided",
          sbtTheSbt % "provided",
          sbtIo % "provided",
          sbtLogging % "provided",
          sbtProcess % "provided")
  )

  // sbt-child process projects
  lazy val sbtRemoteProbe = (
    SbtChildProject("remote-probe")
    settings(Keys.scalaVersion := Dependencies.sbtPluginScalaVersion, Keys.scalaBinaryVersion <<= Keys.scalaVersion)
    dependsOnSource("../protocol")
    dependsOnSource("../../io")
    dependsOn(props, sbtUiInterface % "provided")
    dependsOnRemote(
      sbtMain % "provided",
      sbtTheSbt % "provided",
      sbtIo % "provided",
      sbtLogging % "provided",
      sbtProcess % "provided"
    )
    settings(requiredJars(props, sbtUiInterface))
  )

  // SBT Shims
  lazy val playShimPlugin = (
    SbtShimPlugin("play")
    dependsOn(sbtUiInterface)
    dependsOnRemote(playSbtPlugin)
  )

  lazy val eclipseShimPlugin = (
    SbtShimPlugin("eclipse")
    dependsOn(sbtUiInterface)
    dependsOnRemote(eclipseSbtPlugin)
  )

  lazy val ideaShimPlugin = (
    SbtShimPlugin("idea")
    dependsOn(sbtUiInterface)
    dependsOnRemote(ideaSbtPlugin)
  )

  lazy val defaultsShimPlugin = (
    SbtShimPlugin("defaults")
    // TODO - can we just depend on all the other plugins so we only have one shim?
  )

  val verboseSbtTests = false

  def configureSbtTest(testKey: Scoped) = Seq(
    // set up embedded sbt for tests, we fork so we can set
    // system properties.
    Keys.fork in testKey := true,
    Keys.javaOptions in testKey <<= (
      SbtSupport.sbtLaunchJar,
      Keys.javaOptions in testKey,
      requiredClasspath in sbtRemoteProbe,
      Keys.compile in Compile in sbtRemoteProbe) map {
      (launcher, oldOptions, probeCp, _) =>
        oldOptions ++ Seq("-Dbuilder.sbt.no-shims=true",
                          "-Dbuilder.sbt.launch.jar=" + launcher.getAbsoluteFile.getAbsolutePath,
                          "-Dbuilder.remote.probe.classpath=" + Path.makeString(probeCp.files)) ++
      (if (verboseSbtTests)
        Seq("-Dakka.loglevel=DEBUG",
            "-Dakka.actor.debug.autoreceive=on",
            "-Dakka.actor.debug.receive=on",
            "-Dakka.actor.debug.lifecycle=on")
       else
         Seq.empty)
    })

  lazy val sbtDriver = (
    SbtChildProject("parent")
    settings(Keys.libraryDependencies <+= (Keys.scalaVersion) { v => "org.scala-lang" % "scala-reflect" % v })
    dependsOnSource("../protocol")
    dependsOn(props)
    dependsOn(common)
    dependsOnRemote(akkaActor,
                    sbtLauncherInterface)
    settings(configureSbtTest(Keys.test): _*)
    settings(configureSbtTest(Keys.testOnly): _*)
  )

  lazy val ui = (
    BuilderPlayProject("ui")
    dependsOnRemote(
      commonsIo, mimeUtil, slf4jLog4j,
      sbtLauncherInterface % "provided"
    )
    dependsOn(props, cache, sbtDriver, common, sbtDriver % "test->test")
    settings(play.Project.playDefaultPort := 8888)
    // set up debug props for forked tests
    settings(configureSbtTest(Keys.test): _*)
    settings(configureSbtTest(Keys.testOnly): _*)
    // set up debug props for "run"
    settings(
      // Here we hack the update process that play-run calls to set up everything we need for embedded sbt.
      // Yes, it's a hack.  BUT we *love* hacks right?
      Keys.update <<= (
          SbtSupport.sbtLaunchJar,
          Keys.update,
          requiredClasspath in sbtRemoteProbe,
          Keys.compile in Compile in sbtRemoteProbe,
          // Note: This one should generally push all shim plugins.
          Keys.publishLocal in playShimPlugin) map {
        (launcher, update, probeCp, _, _) =>
          // We register the location after it's resolved so we have it for running play...
          sys.props("builder.sbt.launch.jar") = launcher.getAbsoluteFile.getAbsolutePath
          sys.props("builder.remote.probe.classpath") = Path.makeString(probeCp.files)
          System.err.println("Updating sbt launch jar: " + sys.props("builder.sbt.launch.jar"))
          System.err.println("Remote probe classpath = " + sys.props("builder.remote.probe.classpath"))
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
    BuilderProject("launcher")
    dependsOnRemote(sbtLauncherInterface, sbtCompletion)
    dependsOn(props, common, cache)
  )

  // A hack project just for convenient IvySBT when resolving artifacts into new local repositories.
  lazy val dontusemeresolvers = (
    BuilderProject("dontuseme")
    settings(
      // This hack removes the project resolver so we don't resolve stub artifacts.
      Keys.fullResolvers <<= (Keys.externalResolvers, Keys.sbtResolver) map (_ :+ _),
      Keys.resolvers += Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
      Keys.publish := {},
      Keys.publishLocal := {}
    )
  )
  lazy val it = (
      BuilderProject("integration-tests")
      settings(integration.settings:_*)
      dependsOnRemote(sbtLauncherInterface)
      dependsOn(sbtDriver, props, cache)
      settings(
        com.typesafe.sbtidea.SbtIdeaPlugin.ideaIgnoreModule := true,
        Keys.publish := {}
      )
  )

  lazy val dist = (
    BuilderProject("dist")
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

      localRepoArtifacts <++= (publishedProjects filterNot publishedSbtShimProjects map { ref =>
        // The annoyance caused by cross-versioning.
        (Keys.projectID in ref, Keys.scalaBinaryVersion in ref, Keys.scalaVersion in ref) apply {
          (id, sbv, sv) =>
            CrossVersion(sbv,sv)(id)
        }
      }).join,
      localRepoArtifacts <++= (publishedSbtShimProjects.toSeq map { ref =>
        (Keys.projectID in ref) apply { id =>
            Defaults.sbtPluginExtra(id, sbtPluginVersion, sbtPluginScalaVersion)
        }
      }).join,
      localRepoArtifacts ++=
        Seq("org.scala-sbt" % "sbt" % Dependencies.sbtVersion,
            // For some reason, these are not resolving transitively correctly!
            "org.scala-lang" % "scala-compiler" % Dependencies.sbtPluginScalaVersion,
            "org.scala-lang" % "scala-compiler" % Dependencies.scalaVersion,
            // TODO - Versions in Dependencies.scala
            "net.java.dev.jna" % "jna" % "3.2.3",
            "commons-codec" % "commons-codec" % "1.3",
            "org.apache.httpcomponents" % "httpclient" % "4.0.1",
            "com.google.guava" % "guava" % "11.0.2",
            "xml-apis" % "xml-apis" % "1.0.b2",
            // USED BY templates. TODO - autofind these
            "org.scalatest" % "scalatest_2.10" % "1.9.1",
            "org.webjars" % "webjars-play" % "2.1.0",
            "org.webjars" % "bootstrap" % "2.3.1"
        ),
      localRepoArtifacts ++= {
        val sbt = sbtPluginVersion
        val scala = sbtPluginScalaVersion
        Seq(
          Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-site" % "0.6.0", sbt, scala),
          Defaults.sbtPluginExtra("com.typesafe" % "sbt-native-packager" % "0.4.3", sbt, scala),
          Defaults.sbtPluginExtra("play" % "sbt-plugin" % "2.1.1", sbt, scala),
          Defaults.sbtPluginExtra("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.0", sbt, scala),
          Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-pgp" % "0.8", sbt, scala)
        )
      },
      Keys.mappings in S3.upload <<= (Keys.packageBin in Universal, Keys.version) map { (zip, v) =>
        Seq(zip -> ("typesafe-builder/%s/typesafe-builder-%s.zip" format (v, v)))
      },
      S3.host in S3.upload := "downloads.typesafe.com.s3.amazonaws.com",
      S3.progress in S3.upload := true
    )
  )
}
