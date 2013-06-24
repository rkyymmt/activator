package com.typesafe.sbtrc

import xsbti.AppConfiguration
import xsbti.ApplicationID
import java.io.File
import activator.properties.ActivatorProperties._

/**
 * A trait which can create the SBT child process
 * arguments.   Note:  Since we need access to the launcher, for
 * distributed SNAP, we make this something that can be passed in and extended
 * so that we can have a stub implementation.
 */
trait SbtProcessLauncher {
  def arguments(port: Int): Seq[String]
}

/**
 * This class is able to create the command line for Sbt child processes
 * using the launcher to discover the controller jars.
 *
 * @param configuration  The AppConfiguration passed to an application
 *                       via the SBT launcher.   We use this to lookup the controller jars.
 */
class DefaultSbtProcessLauncher(configuration: AppConfiguration) extends SbtProcessLauncher {
  // The launcher interface for resolving more STUFF
  private def launcher = configuration.provider.scalaProvider.launcher
  // The Application for the controller jars.  We can use this to get the classpath.
  private object probeApp extends ApplicationID {
    // TODO - Pull these constants from some build-generated properties or something.
    def groupID = "com.typesafe.activator"
    def name = "sbt-rc-controller"
    def version = configuration.provider.id.version // Cheaty way to get version
    def mainClass = "com.typesafe.sbtrc.SetupSbtChild" // TODO - What main class?
    def mainComponents = Array[String]("") // TODO - is this correct.
    def crossVersioned = false
    def classpathExtra = Array[File]()
  }

  // This will resolve the probe artifact using our launcher and then
  // give us the classpath
  private lazy val probeClassPath: Seq[File] =
    launcher.app(probeApp, SBT_SCALA_VERSION).mainClasspath

  // TODO - Find the launcher.

  def arguments(port: Int): Seq[String] = {
    val portArg = "-Dactivator.sbt-rc-port=" + port.toString
    // TODO - These need to be configurable *and* discoverable.
    // we have no idea if computers will be able to handle this amount of
    // memory....
    val defaultJvmArgs = Seq(
      "-Xss1024K",
      "-Xmx" + SBT_XMX,
      "-XX:PermSize=" + SBT_PERMSIZE,
      "-XX:+CMSClassUnloadingEnabled")
    // TODO - handle spaces in strings and such...
    val sbtProps = Seq(
      "-Dactivator.home=" + ACTIVATOR_HOME,
      // TODO - better handling of missing sbt.boot.directory property!
      "-Dsbt.boot.directory=" + (sys.props get "sbt.boot.directory" getOrElse (sys.props("user.home") + "/.sbt")),
      // TODO - Don't allow user-global plugins?
      //"-Dsbt.global.base=/tmp/.sbtboot",
      portArg)
    val launcher = new java.io.File(ACTIVATOR_LAUNCHER_JAR)
    val jar = Seq("-jar", launcher.getAbsolutePath)

    // TODO - Is the cross-platform friendly?
    val probeClasspathString =
      "\"\"\"" + ((probeClassPath map (_.getAbsolutePath)).distinct mkString File.pathSeparator) + "\"\"\""
    val escapedPcp = probeClasspathString.replaceAll("\\\\", "/")
    val sbtcommands = Seq(
      s"apply -cp $escapedPcp com.typesafe.sbtrc.SetupSbtChild",
      "listen")

    val result = Seq("java") ++
      defaultJvmArgs ++
      sbtProps ++
      jar ++
      sbtcommands

    System.err.println("Running sbt-child with arguments =\n\t" + result.mkString("\n\t"))

    result
  }
}
