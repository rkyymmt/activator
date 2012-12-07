package com.typesafe.sbtchild

import scala.sys.process.Process
import java.io.File

class SbtChild(workingDir: File) {
  private val serverSocket = IPC.openServerSocket()
  private val port = serverSocket.getLocalPort()

  // FIXME don't hardcode my homedir (we want the launcher to be part of snap)
  private val process = Process(Seq("java",
    "-Dsnap.sbt-child-port=" + port,
    "-Dsbt.boot.directory=/home/hp/.sbt/boot",
    "-Xss1024K", "-Xmx1024M", "-XX:PermSize=512M", "-XX:+CMSClassUnloadingEnabled",
    "-jar",
    "/opt/hp/bin/sbt-launch-0.12.0.jar",
    // command to add our special hook
    "apply com.typesafe.sbt.SetupSbtChild",
    // enter the "get stuff from the socket" loop
    "listen"),
    workingDir)

  process.run()

  val server = IPC.accept(serverSocket)
}

object SbtChild {
  def apply(workingDir: File) = new SbtChild(workingDir)
}
