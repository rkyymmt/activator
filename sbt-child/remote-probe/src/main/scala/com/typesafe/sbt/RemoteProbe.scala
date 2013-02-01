package com.typesafe.sbtchild

import _root_.sbt._
import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope
import com.typesafe.sbtchild._
import sbt.Aggregation.KeyValue
import sbt.complete.DefaultParsers
import sbt.Load.BuildStructure
import java.net.SocketException
import java.io.EOFException
import java.io.IOException

object SetupSbtChild extends (State => State) {
  // this is the entry point invoked by sbt
  override def apply(s: State): State = {
    s ++ Seq(listen)
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

  private def runInputTask[T](key: ScopedKey[T], extracted: Extracted, state: State): State = {
    implicit val display = Project.showContextKey(state)
    val it = extracted.get(SettingKey(key.key) in key.scope)
    val keyValues = KeyValue(key, it) :: Nil
    val parser = Aggregation.evaluatingParser(state, extracted.structure, show = false)(keyValues)
    DefaultParsers.parse("", parser) match {
      case Left(message) =>
        throw new Exception("Failed to run task: " + display(key) + ": " + message)
      case Right(f) =>
        f()
    }
  }

  private def handleRequest(req: protocol.Envelope, origState: State, logger: CaptureLogger): State = {
    val ref = Project.extract(origState).currentRef
    val extracted = Extracted(Project.structure(origState), Project.session(origState), ref)(Project.showFullKey)

    req match {
      case protocol.Envelope(serial, replyTo, protocol.NameRequest) =>
        val result = extracted.get(name)
        System.err.println("Logs are: " + logger.get)
        client.replyJson(serial, protocol.NameResponse(result, logger.get))
        origState
      case protocol.Envelope(serial, replyTo, protocol.CompileRequest) =>
        try {
          val (s, result) = extracted.runTask(compile in Compile, origState)
          System.err.println("Logs are: " + logger.get)
          client.replyJson(serial, protocol.CompileResponse(logger.get))
          s
        } catch {
          case e: Exception =>
            client.replyJson(serial, protocol.ErrorResponse(e.getMessage, logger.get))
            origState
        }
      case protocol.Envelope(serial, replyTo, protocol.RunRequest) =>
        try {
          val s = runInputTask(run in Compile, extracted, origState)
          System.err.println("Logs are: " + logger.get)
          client.replyJson(serial, protocol.RunResponse(logger.get))
          s
        } catch {
          case e: Exception =>
            client.replyJson(serial, protocol.ErrorResponse(e.getMessage, logger.get))
            origState
        }
      case _ => {
        client.replyJson(req.serial, protocol.ErrorResponse("Unknown request: " + req, logger.get))
        origState
      }
    }
  }

  val listen = Command.command(ListenCommandName, Help.more(ListenCommandName, "listens for remote commands")) { origState =>
    val req = try {
      protocol.Envelope(client.receive())
    } catch {
      case e: IOException =>
        System.err.println("Lost connection to parent process: " + e.getClass.getSimpleName() + ": " + e.getMessage())
        System.exit(0)
        throw new RuntimeException("not reached") // compiler doesn't know that System.exit never returns
    }

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
