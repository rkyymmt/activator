package com.typesafe.sbtchild

import builder.properties.BuilderProperties.BUILDER_HOME
import java.io.File

/**
 * This guy is responsible for finding all the debug info we use when running
 * inside the SBT UI so we can run sbt children.
 */
object DebugSbtChildProcessMaker extends SbtChildProcessMaker {
  private val probeClassPathProp = "builder.remote.probe.classpath"
  private val sbtLauncherJarProp = "builder.sbt.launch.jar"
  private val allNeededProps = Seq(probeClassPathProp, sbtLauncherJarProp)

  // NOTE -> THIS HAS TO BE LAZY
  // These values are only available when we run a debug build locally.
  // When trying to run the UI now, things explode because the script (appropriately)
  // Does not specify these things.
  private def assertPropsArentMissing(): Unit = {
    val missing = allNeededProps.filter(sys.props(_) eq null)
    if (missing.nonEmpty)
      throw new RuntimeException("DebugSbtChildProcessMaker requires system props: " + missing)
  }

  private lazy val probeClassPath: Seq[File] = (sys.props(probeClassPathProp) split File.pathSeparator map (n => new File(n)))(collection.breakOut)
  private lazy val commandClasspath: Seq[File] = probeClassPath //filterNot (_.getAbsolutePath contains "ui-interface")
  //private lazy val uiClassDir: Seq[File] = probeClassPath filter (_.getAbsolutePath contains "ui-interface")
  private lazy val sbtLauncherJar: String = sys.props(sbtLauncherJarProp)

  //private lazy val launchClasspath: Seq[File] = uiClassDir ++ Seq(new File(sbtLauncherJar))

  def cp(files: Seq[File]): String = (files map (_.getAbsolutePath)).distinct mkString File.pathSeparator

  def arguments(port: Int): Seq[String] = {
    assertPropsArentMissing()
    val portArg = "-Dbuilder.sbt-child-port=" + port.toString
    // TODO - These need to be configurable *and* discoverable.
    // we have no idea if computers will be able to handle this amount of
    // memory....
    val defaultJvmArgs = Seq(
      "-Xss1024K",
      "-Xmx1024M",
      "-XX:PermSize=512M",
      "-XX:+CMSClassUnloadingEnabled")
    val sbtProps = Seq(
      "-Dbuilder.home=" + BUILDER_HOME,
      // Looks like this one is unset...
      "-Dsbt.boot.directory=" + (sys.props get "sbt.boot.directory" getOrElse (sys.props("user.home") + "/.sbt")),
      // TODO - Don't allow user-global plugins?
      //"-Dsbt.global.base=/tmp/.sbtboot",
      portArg)
    // We need to get the ui interface classes *earlier* in the classpath...
    // This is a hack specifically for running debug runs, so that the classpath is correct.
    // In production, we do this via the launcher...
    val jar = Seq("-jar", sbtLauncherJar)
    //Seq("-classpath", cp(launchClasspath), "xsbt.boot.Boot")
    val sbtcommands = Seq(
      "apply -cp " + cp(commandClasspath) + " com.typesafe.sbtchild.SetupSbtChild",
      "listen")
    val result = Seq("java") ++
      defaultJvmArgs ++
      sbtProps ++
      jar ++
      sbtcommands

    System.out.println(result.mkString("Sbt Debug command:\n\t", "\n\t", ""))

    result
  }
}
