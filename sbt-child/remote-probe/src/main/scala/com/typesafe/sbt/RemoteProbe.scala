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

    def exceptionsToErrorResponse(serial: Long)(block: => State): State = {
      try {
        block
      } catch {
        case e: Exception =>
          client.replyJson(serial,
            protocol.ErrorResponse("exception during sbt task: " + e.getClass.getSimpleName + ": " + e.getMessage))
          origState
      }
    }

    req match {
      case protocol.Envelope(serial, replyTo, protocol.NameRequest(_)) =>
        val result = extracted.get(name)
        client.replyJson(serial, protocol.NameResponse(result))
        origState
      case protocol.Envelope(serial, replyTo, protocol.CompileRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val (s, result) = extracted.runTask(compile in Compile, origState)
          client.replyJson(serial, protocol.CompileResponse(success = true))
          s
        }
      case protocol.Envelope(serial, replyTo, protocol.RunRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val s = runInputTask(run in Compile, extracted, origState)
          client.replyJson(serial, protocol.RunResponse(success = true))
          s
        }
      case _ => {
        client.replyJson(req.serial, protocol.ErrorResponse("Unknown request: " + req))
        origState
      }
    }
  }

  val listen = Command.command(ListenCommandName, Help.more(ListenCommandName, "listens for remote commands")) { origState =>
    val req = try protocol.Envelope(client.receive()) catch {
      case e: IOException =>
        System.err.println("Lost connection to parent process: " + e.getClass.getSimpleName() + ": " + e.getMessage())
        System.exit(0)
        throw new RuntimeException("not reached") // compiler doesn't know that System.exit never returns
    }

    val newLogger = new CaptureLogger(client, req.serial, origState.globalLogging.full)
    val newLogging = origState.globalLogging.copy(full = newLogger)
    val loggedState = origState.copy(globalLogging = newLogging)

    val afterTaskState: State = handleRequest(req, loggedState, newLogger)

    val newState = afterTaskState.copy(onFailure = Some(ListenCommandName),
      remainingCommands = ListenCommandName +: origState.remainingCommands,
      globalLogging = origState.globalLogging)
    newState
  }

  private class CaptureLogger(client: ipc.Client, requestSerial: Long, delegate: Logger) extends Logger {
    private def add(entry: protocol.LogEntry) = synchronized {
      client.replyJson(requestSerial, protocol.LogEvent(entry))
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
