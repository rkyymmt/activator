package com.typesafe.sbt

import _root_.sbt._

import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope
import com.typesafe.sbtchild.IPC
import com.typesafe.sbtchild.Protocol._

object SbtChild extends Plugin {
  override lazy val settings = Seq.empty

  ///// Settings keys

  object SbtChildKeys {

  }

  private def getPort(): Int = {
    val portString = System.getProperty("snap.sbt-child-port")
    if (portString == null)
      throw new Exception("No port property set")
    val port = Integer.parseInt(portString)
    port
  }

  val ListenCommandName = "listen"

  val listen = Command.command(ListenCommandName, Help.more(ListenCommandName, "listens for remote commands")) { origState =>
    val client = IPC.openClient(getPort())
    val line: Option[String] = Some("") // FIXME
    line match {
      case Some(line) =>
        val newState = origState.copy(onFailure = Some(ListenCommandName),
          remainingCommands = line +: ListenCommandName +: origState.remainingCommands)
        if (line.trim.isEmpty) newState else newState.clearGlobalLog
      case None => origState
    }
  }
}
