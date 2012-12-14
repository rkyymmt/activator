package com.typesafe.sbt

import _root_.sbt._

import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope
import com.typesafe.sbtchild._

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

  lazy val client = ipc.openClient(getPort())

  val ListenCommandName = "listen"

  private def handleRequest(req: protocol.Envelope, origState: State, logger: CaptureLogger): State = {
    val ref = Project.extract(origState).currentRef
    val extracted = Extracted(Project.structure(origState), Project.session(origState), ref)(Project.showFullKey)

    req match {
      case protocol.Envelope(serial, replyTo, protocol.NameRequest) =>
        val result = extracted.get(name)
        System.err.println("Logs are: " + logger.get)
        client.replySerialized(serial, protocol.NameResponse(result, logger.get))
        origState
      case protocol.Envelope(serial, replyTo, protocol.CompileRequest) =>
        try {
          val (s, result) = extracted.runTask(compile in Compile, origState)
          System.err.println("Logs are: " + logger.get)
          client.replySerialized(serial, protocol.CompileResponse(logger.get))
          s
        } catch {
          case e: Exception =>
            client.replySerialized(serial, protocol.ErrorResponse(e.getMessage, logger.get))
            origState
        }
      case _ =>
        throw new Exception("Bad request: " + req)
    }
  }

  val listen = Command.command(ListenCommandName, Help.more(ListenCommandName, "listens for remote commands")) { origState =>
    val req = protocol.Envelope(client.receive())

    val newLogger = new CaptureLogger(origState.globalLogging.full)
    val newLogging = origState.globalLogging.copy(full = newLogger)
    val loggedState = origState.copy(globalLogging = newLogging)

    val afterTaskState: State = handleRequest(req, loggedState, newLogger)

    val newState = afterTaskState.copy(onFailure = Some(ListenCommandName),
      remainingCommands = ListenCommandName +: origState.remainingCommands,
      globalLogging = origState.globalLogging)
    newState
  }

  private class CaptureLogger(delegate: Logger) extends Logger {
    var entries: List[protocol.LogEntry] = Nil

    private def add(entry: protocol.LogEntry) = synchronized {
      // prepend, so we have to reverse later
      entries = entry :: entries
    }

    def get: List[protocol.LogEntry] = synchronized {
      // logs were built via prepend
      entries.reverse
    }

    def trace(t: => Throwable): Unit = {
      add(protocol.LogTrace(t.getClass.getSimpleName, t.getMessage))
    }
    def success(message: => String): Unit = {
      add(protocol.LogSuccess(message))
    }
    def log(level: Level.Value, message: => String): Unit = {
      add(protocol.LogMessage(level.id, message))
    }
  }
}

object SetupSbtChild extends (State => State) {
  override def apply(s: State): State = {
    s ++ Seq(SbtChild.listen)
  }
}
