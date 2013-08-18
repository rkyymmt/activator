package activator

import xsbti.{ AppMain, AppConfiguration }
import java.awt._
import java.awt.event._
import javax.swing._
import java.net.HttpURLConnection
import java.net.URL
import sbt.IO
import com.typesafe.sbtrc.launching.DefaultSbtProcessLauncher
import snap._
import activator.properties.ActivatorProperties._
import xsbti.GlobalLock

object PidDetector {
  // Converts to callable
  private[PidDetector] def f2c[A](f: => A): java.util.concurrent.Callable[A] =
    new java.util.concurrent.Callable[A] {
      def call: A = f
    }
  // Helper to make global lock easier to use.
  implicit class LockHelper(val lock: GlobalLock) extends AnyVal {
    def onBuilder[A](f: => A): A =
      lock.apply(ACTIVATOR_LOCK_FILE, f2c(f))
  }
  // Checks to see if we can load the previous pid.
  private def previousPid(lock: GlobalLock): Option[String] =
    lock onBuilder {
      if (ACTIVATOR_PID_FILE.exists)
        Option(IO.readLines(ACTIVATOR_PID_FILE).head) filterNot (_.isEmpty)
      else None
    }

  private def writeCurrentPid(lock: GlobalLock): Unit = {
    for (pid <- java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split('@').headOption) {
      lock onBuilder {
        IO.write(ACTIVATOR_PID_FILE, pid, append = false)
      }
    }
  }
  import sys.process._
  private def psListHasPid(pid: String, pb: ProcessBuilder): Boolean =
    try {
      for {
        line <- pb.lines
        p <- line.split("\\s+").filterNot(_.isEmpty).headOption
        if p contains pid
      } return true
      false
    } catch {
      // TODO - What kind of process failure exceptions do we have to ignore?
      case e: Exception => // Ignore
        false
    }
  private def findProcessLinux(pid: String): Boolean =
    psListHasPid(pid, Seq("ps", "-e"))
  private def findProcessWindows(pid: String): Boolean =
    psListHasPid(pid, Seq("cmd", "/c", "jps"))

  // Process detection on both windows and linux...
  // These default to returning false if they can't detect proceses at all.
  private def findProcess(pid: String): Boolean =
    if (sys.props("os.name").toLowerCase contains "windows") findProcessWindows(pid)
    else findProcessLinux(pid)

  /**
   * Checks to see if there is a previous running application.
   * If so, we return true, otherwise we return false.
   *
   * SIDE EFFECT WARNING - This updates the PID file and does file locking.
   */
  def checkRunning(lock: GlobalLock): Boolean = {
    val checkPrevious =
      (for {
        pid <- previousPid(lock)
        _ = println("Found previous process id: " + pid)
        if findProcess(pid)
      } yield true) getOrElse false

    if (!checkPrevious) writeCurrentPid(lock)
    checkPrevious
  }
}

/** Expose for SBT launcher support. */
class UIMain extends AppMain {

  def run(configuration: AppConfiguration) = {

    val lock = configuration.provider.scalaProvider.launcher.globalLock
    // First we check to see if a previous app is running..
    val alreadyRunning = PidDetector.checkRunning(lock)

    // First set up the server port.
    System.setProperty("http.port", serverPort.toString)

    // Play defaults to 0.0.0.0 which listens on all
    // interfaces, we want loopback only.
    System.setProperty("http.address", "127.0.0.1")

    if (!alreadyRunning) {
      // locate sbt details and store in a singleton
      Global.installSbtLauncher(new DefaultSbtProcessLauncher(configuration))

      // Start the Play app... (TODO - how do we know when we're done?)
      // TODO - Is this hack ok?
      withContextClassloader(play.core.server.NettyServer.main(configuration.arguments))

      // Delay opening the browser a short bit so play can start.
      waitForServerStartup()
    } else {
      System.err.println("Connecting to existing Activator UI server...")
    }

    // openBrowser
    openBrowser()

    // Prevent us from dropping to exit immediately... block on Play running until you see CTRL-C or some such....
    if (!alreadyRunning) waitForever()
    // TODO - Catch errors and better return value.
    Exit(0)
  }

  // TODO - make sure this is open!
  lazy val serverPort: Int = 8888

  def waitForServerStartup(): Unit = {
    import java.net._
    def isAlive: Boolean = {
      HttpURLConnection setFollowRedirects true
      val url = new URL(f"http://localhost:${serverPort}%d/")
      val request = url.openConnection()
      request setDoOutput true
      val http = request.asInstanceOf[HttpURLConnection]
      http setRequestMethod "GET"
      try {
        http.connect()
        val response = http.getResponseCode
        response == HttpURLConnection.HTTP_OK
      } finally http.disconnect()
    }
    // Keep sleeping until we see the server respond.
    val secondsToWait = 60
    // remaining = half-second ticks
    def checkAlive(remaining: Int = secondsToWait * 2): Unit =
      if (!isAlive) remaining match {
        case 0 => sys error "Web server never started!"
        case _ =>
          Thread sleep 500L
          checkAlive(remaining - 1)
      }
    checkAlive()
  }

  def waitForever(): Unit = {
    // TODO - figure out a better way to do this intead of parking a thread.
    this.synchronized(this.wait())
  }

  // TODO - detect port?
  def openBrowser() = {
    val desktop: Option[Desktop] =
      if (Desktop.isDesktopSupported)
        Some(Desktop.getDesktop) filter (_ isSupported Desktop.Action.BROWSE)
      else None

    desktop match {
      case Some(d) => d browse new java.net.URI(f"http://localhost:${serverPort}%d/")
      case _ => showError("""|Unable to open a web browser!
                                   |Please point your browser at:
                                   | http://localhost:%d/""".stripMargin format (serverPort))
    }
  }

  def withContextClassloader[A](f: => A): A = {
    val current = Thread.currentThread
    val old = current.getContextClassLoader
    current setContextClassLoader getClass.getClassLoader
    try f
    finally current setContextClassLoader old
  }

  // TODO - Is it ok to use swing?  We can detect that actually....
  def showError(errorMsg: String): Unit = {
    val isHeadless = try {
      GraphicsEnvironment.isHeadless
    } catch {
      case _: Exception => true
    }

    if (isHeadless)
      System.err.println(errorMsg)
    else {
      // create and configure a text area - fill it with exception text.
      val textArea = new JTextArea
      textArea setFont new Font("Sans-Serif", Font.PLAIN, 16)
      textArea setEditable false
      textArea setText errorMsg
      textArea setLineWrap true

      // stuff it in a scrollpane with a controlled size.
      val scrollPane = new JScrollPane(textArea)
      scrollPane setPreferredSize new Dimension(350, 150)

      // pass the scrollpane to the joptionpane.
      JOptionPane.showMessageDialog(null, scrollPane, "O SNAP!", JOptionPane.ERROR_MESSAGE)
    }
  }
  // Wrapper to return exit codes.
  case class Exit(val code: Int) extends xsbti.Exit
}
