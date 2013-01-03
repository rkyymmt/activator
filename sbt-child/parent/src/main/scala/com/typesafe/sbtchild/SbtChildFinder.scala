package com.typesafe.sbtchild

import xsbti.AppConfiguration
import xsbti.ApplicationID
import java.io.File
import snap.properties.SnapProperties


/** A trait which can create the SBT child process
 * arguments.   Note:  Since we need access to the launcher, for
 * distributed SNAP, we make this something that can be passed in and extended
 * so that we can have a stub implementation.
 */
trait SbtChildProcessMaker {
  def arguments(port: Int): Seq[String] 
}

// The stubbed out child process maker for havoc's computer/testing.
object HavocsSbtChildProcessmaker extends SbtChildProcessMaker {
  def arguments(port: Int): Seq[String] = Seq("java",
    "-Dsnap.sbt-child-port=" + port,
    "-Dsbt.boot.directory=/home/hp/.sbt/boot",
    "-Xss1024K", "-Xmx1024M", "-XX:PermSize=512M", "-XX:+CMSClassUnloadingEnabled",
    "-jar",
    "/opt/hp/bin/sbt-launch-0.12.0.jar",
    // command to add our special hook
    "apply com.typesafe.sbtchild.SetupSbtChild",
    // enter the "get stuff from the socket" loop
    "listen")
}


/** This class is able to create the command line for SbtChildProbe processes
 * using the launcher to discover the child probe.
 * 
 * @param configuration  The AppConfiguration passed to an application
 *                       via the SBT launcher.   We use this to lookup the probe jars.
 */
class SbtChildLauncher(configuration: AppConfiguration) extends SbtChildProcessMaker {
  // The launcher interface for resolving more STUFF
  private def launcher = configuration.provider.scalaProvider.launcher
  // The Application for the child probe.  We can use this to get the classpath.
  private object probeApp extends ApplicationID {
    // TODO - Pull these constants from some build-generated properties or something.
    def groupID = "com.typesafe.snap" 
    def name = "sbt-child-remote-probe"
    def version = configuration.provider.id.version  // Cheaty way to get version
    def mainClass = "com.typesafe.sbtchild.SetupSbtChild" // TODO - What main class?
    def mainComponents = Array[String]("")  // TODO - is this correct.
    def crossVersioned = false
    def classpathExtra = Array[File]()
  }
  
  // This will resolve the probe artifact using our launcher and then
  // give us the classpath
  private lazy val probeClassPath: Seq[File] =
    launcher.app(probeApp, SnapProperties.SBT_SCALA_VERSION).mainClasspath
    
  // TODO - Find the launcher.
  
    
    
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
    // TODO - handle spaces in strings and such...
    val sbtProps = Seq(
      "-Dsnap.home="+SnapProperties.SNAP_HOME,
      "-Dsbt.boot.directory="+sys.props("sbt.boot.directory"),
      portArg)
    val jar = Seq("-jar", SnapProperties.SNAP_LAUNCHER_JAR)
    
    // TODO - Is the cross-platform friendly?
    val probeClasspathString = (probeClassPath map (_.getAbsolutePath)).distinct mkString File.pathSeparator
    val sbtcommands = Seq(
      "apply -cp :" + probeClasspathString + " com.typesafe.sbtchild.SetupSbtChild",
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
