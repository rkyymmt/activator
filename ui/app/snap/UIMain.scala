package snap

import xsbti.{AppMain, AppConfiguration}
import java.awt._
import java.awt.event._
import javax.swing._
import com.typesafe.sbtchild.SbtChildLauncher

/** Expose for SBT launcher support. */
class UIMain extends AppMain {

  def run(configuration: AppConfiguration) = {
    // First set up the server port.
    System.setProperty("http.port", serverPort.toString)

    // locate sbt details and store in a singleton
    controllers.api.App.sbtChildProcessMaker = new SbtChildLauncher(configuration)

    // Start the Play app... (TODO - how do we know when we're done?)
    // TODO - Is this hack ok?
    withContextClassloader(play.core.server.NettyServer.main(configuration.arguments))

    // Delay opening the browser a short bit so play can start.
    waitForServerStartup()

    // openBrowser
    openBrowser()

    // Prevent us from dropping to exit immediately... block on Play running until you see CTRL-C or some such....
    waitForever()
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
    // Default to waiting 30 seconds.
    def checkAlive(remaining: Int = 60): Unit =
      if(!isAlive) remaining match {
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
      if(Desktop.isDesktopSupported)
        Some(Desktop.getDesktop) filter (_ isSupported Desktop.Action.BROWSE)
      else None

    desktop match {
      case Some(d) => d browse new java.net.URI(f"http://localhost:${serverPort}%d/")
      case _       => showError("""|Unable to open a web browser!
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
  // Wrapper to return exit codes.
  case class Exit(val code: Int) extends xsbti.Exit
}
