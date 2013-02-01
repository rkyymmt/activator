package com.typesafe.sbtchild

import snap.properties.SnapProperties
import java.io.File

/**
 * This guy is responsible for finding all the debug info we use when running
 * inside the SBT UI so we can run sbt children.
 */
object DebugSbtChildProcessMaker extends SbtChildProcessMaker {
  private val probeClassPathProp = "snap.remote.probe.classpath"
  private val sbtLauncherJarProp = "snap.sbt.launch.jar"
  private val allNeededProps = Seq(probeClassPathProp, sbtLauncherJarProp)

  {
    val missing = allNeededProps.filter(sys.props(_) eq null)
    if (missing.nonEmpty)
      throw new RuntimeException("DebugSbtChildProcessMaker requires system props: " + missing)
  }

  private lazy val probeClassPath: Seq[File] = Seq(new File(sys.props(probeClassPathProp)))
  private lazy val sbtLauncherJar: String = sys.props(sbtLauncherJarProp)

  def arguments(port: Int): Seq[String] = {
    val portArg = "-Dsnap.sbt-child-port=" + port.toString
    // TODO - These need to be configurable *and* discoverable.
    // we have no idea if computers will be able to handle this amount of
    // memory....
    val defaultJvmArgs = Seq(
      "-Xss1024K",
      "-Xmx1024M",
      "-XX:PermSize=512M",
      "-XX:+CMSClassUnloadingEnabled")
    val sbtProps = Seq(
      "-Dsnap.home=" + SnapProperties.SNAP_HOME,
      // Looks like this one is unset...
      "-Dsbt.boot.directory=" + (sys.props get "sbt.boot.directory" getOrElse (sys.props("user.home") + "/.sbt")),
      // TODO - Don't allow user-global plugins?
      //"-Dsbt.global.base=/tmp/.sbtboot",
      portArg)
    val jar = Seq("-jar", sbtLauncherJar)
    val probeClasspathString = (probeClassPath map (_.getAbsolutePath)).distinct mkString File.pathSeparator
    val sbtcommands = Seq(
      "apply -cp " + probeClasspathString + " com.typesafe.sbtchild.SetupSbtChild",
      "listen")
    val result = Seq("java") ++
      defaultJvmArgs ++
      sbtProps ++
      jar ++
      sbtcommands
    result
  }
}
