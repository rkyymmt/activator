package snap

import com.typesafe.sbtchild.SbtChildProcessMaker
import snap.properties.SnapProperties
import java.io.File

/**
 * This guy is responsible for finding all the debug info we use when running
 * inside the SBT UI so we can run sbt children.
 */
object DebugSbtChildProcessMaker extends SbtChildProcessMaker {

  private lazy val probeClassPath: Seq[File] = Seq(new File(sys.props("snap.remote.probe.classpath")))
  private lazy val sbtLauncherJar: String = sys.props("snap.sbt.launch.jar")

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
      "-Dsbt.boot.directory=" + sys.props("sbt.boot.directory"),
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