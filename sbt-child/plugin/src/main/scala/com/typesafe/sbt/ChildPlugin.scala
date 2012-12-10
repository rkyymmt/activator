package com.typesafe.sbt

import _root_.sbt._

import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope
import com.typesafe.sbtchild.IPC
import com.typesafe.sbtchild.Protocol

// this is a Plugin but currently we don't use it as one
// (see SetupSbtChild below)
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

  lazy val client = IPC.openClient(getPort())

  val ListenCommandName = "listen"

  val listen = Command.command(ListenCommandName, Help.more(ListenCommandName, "listens for remote commands")) { origState =>
    val req = Protocol.Envelope(client.receive())

    val ref = Project.extract(origState).currentRef
    val extracted = Extracted(Project.structure(origState), Project.session(origState), ref)(Project.showFullKey)

    val afterTaskState: State = req match {
      case Protocol.Envelope(serial, replyTo, Protocol.NameRequest) =>
        val result = extracted.get(name)
        client.replySerialized(serial, Protocol.NameResponse(result))
        origState
      case Protocol.Envelope(serial, replyTo, Protocol.CompileRequest) =>
        System.err.println("Running compile...")
        val (s, result) = extracted.runTask(compile, origState)
        System.err.println("Replying to compile...")
        client.replySerialized(serial, Protocol.CompileResponse)
        s
      case _ =>
        throw new Exception("Bad request: " + req)
    }

    val newState = afterTaskState.copy(onFailure = Some(ListenCommandName),
      remainingCommands = ListenCommandName +: origState.remainingCommands)
    newState
  }
}

object SetupSbtChild extends (State => State) {
  override def apply(s: State): State = {
    s ++ Seq(SbtChild.listen)
  }
}
