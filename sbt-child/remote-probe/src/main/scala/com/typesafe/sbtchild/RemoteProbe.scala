package com.typesafe.sbtchild

import _root_.sbt._
import Project.Initialize
import Keys.logManager
import Scope.GlobalScope
import com.typesafe.sbtchild._
import sbt.Aggregation.KeyValue
import sbt.complete.DefaultParsers
import sbt.Load.BuildStructure
import java.net.SocketException
import java.io.EOFException
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import scala.util.matching.Regex
import com.typesafe.sbt.ui
import scala.util.parsing.json._

object SetupSbtChild extends (State => State) {

  import SbtUtil._

  private lazy val client = ipc.openClient(getPort())

  val ListenCommandName = "listen"

  // this is the entry point invoked by sbt
  override def apply(s: State): State = {
    val betweenRequestsLogger = new EventLogger(client, 0L)
    // Make sure the shims are installed we need for this build.
    // TODO - Better place/way to do this?
    PlaySupport.ensureShim(s)
    addLogger(s, betweenRequestsLogger.toGlobalLogging) ++ Seq(listen)
  }

  private def addLogger(origState: State, logging: GlobalLogging): State = {
    addLogManager(origState.copy(globalLogging = logging), logging.full)
  }

  private def withLogger(origState: State, logging: GlobalLogging)(f: State => State): State = {
    // This never restores the original LogManager, for now it doesn't matter since
    // it does restore one that uses origState.globalLogging.full which will be the
    // logger we want.
    addLogger(f(addLogger(origState, logging)), origState.globalLogging)
  }

  private case class ContextIndifferentLogManager(logger: Logger) extends LogManager {
    override def apply(data: Settings[Scope], state: State, task: ScopedKey[_], writer: PrintWriter): Logger = logger
  }

  private def addLogManager(state: State, logger: Logger): State = {
    val (extracted, ref) = extractWithRef(state)

    val settings = makeAppendSettings(Seq(logManager := ContextIndifferentLogManager(logger)), ref, extracted)

    reloadWithAppended(state, settings)
  }

  private def newUIContext(serial: Long, taskName: String) = new ui.Context() {
    override def isCanceled = false // TODO
    override def updateProgress(progress: ui.Progress, status: Option[String]) = {} // TODO
    override def sendEvent(id: String, event: ui.Params) = {
      client.replyJson(serial, protocol.GenericEvent(task = taskName, id = id, params = event.toMap))
    }
    override def take(): ui.Status = ui.NoUIPresent // TODO
    override def peek(): Option[ui.Status] = None // TODO
  }

  private def getPort(): Int = {
    val portString = System.getProperty("builder.sbt-child-port")
    if (portString == null)
      throw new Exception("No port property set")
    val port = Integer.parseInt(portString)
    port
  }

  private def handleRequest(req: protocol.Envelope, origState: State): State = {
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
      case protocol.Envelope(serial, replyTo, protocol.GenericRequest(sendEvents, taskName, paramsMap)) =>
        exceptionsToErrorResponse(serial) {
          ui.findHandler(taskName, origState) map { handler =>
            client.replyJson(req.serial, protocol.RequestReceivedEvent)
            val context = newUIContext(serial, taskName)
            val params = ui.Params.fromMap(paramsMap)
            val (newState, replyParams) = handler(origState, context, params)
            client.replyJson(serial, protocol.GenericResponse(name = taskName,
              params = replyParams.toMap))
            newState
          } getOrElse {
            client.replyJson(req.serial, protocol.ErrorResponse("No handler for: " + taskName))
            origState
          }
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

    val newLogger = new EventLogger(client, req.serial)

    withLogger(origState, newLogger.toGlobalLogging) { loggedState =>
      val afterTaskState: State = handleRequest(req, loggedState)

      val newState = afterTaskState.copy(onFailure = Some(ListenCommandName),
        remainingCommands = ListenCommandName +: afterTaskState.remainingCommands)
      newState
    }
  }

  // requestSerial would be 0 for "not during a request"
  private class EventLogger(client: ipc.Client, requestSerial: Long) extends Logger {
    def send(entry: protocol.LogEntry) = {
      client.replyJson(requestSerial, protocol.LogEvent(entry))
    }

    def trace(t: => Throwable): Unit = {
      send(protocol.LogTrace(t.getClass.getSimpleName, t.getMessage))
    }

    def success(message: => String): Unit = {
      send(protocol.LogSuccess(message))
    }

    def log(level: Level.Value, message: => String): Unit = {
      send(protocol.LogMessage(level.toString, message))
    }

    private val ansiCodeRegex = "\\033\\[[0-9;]+m".r
    private val logLevelRegex = new Regex("^\\[([a-z]+)\\] *(.*)", "level", "message")

    private def logLine(line: String): Unit = {
      val noCodes = ansiCodeRegex.replaceAllIn(line, "")
      logLineNoCodes(noCodes)
    }

    // log a "cooked" line (that already has [info] prepended etc.)
    private def logLineNoCodes(line: String): Unit = {
      val entry: protocol.LogEntry = logLevelRegex.findFirstMatchIn(line) flatMap { m =>
        val levelString = m.group("level")
        val message = m.group("message")
        Level(levelString) match {
          case Some(level) => Some(protocol.LogMessage(level.toString, message))
          case None => levelString match {
            case "success" => Some(protocol.LogSuccess(message))
            case _ => None
          }
        }
      } getOrElse {
        protocol.LogMessage(Level.Info.toString, line)
      }
      send(entry)
    }

    private def throwawayBackingFile = java.io.File.createTempFile("builder-", ".log")

    private def newBacking = GlobalLogBacking(file = throwawayBackingFile,
      last = None,
      newLogger = (writer, oldBacking) => toGlobalLogging,
      newBackingFile = () => throwawayBackingFile)

    def toGlobalLogging: GlobalLogging = {
      GlobalLogging(this, ConsoleLogger(consoleOut), newBacking)
    }

    private val consoleBuf = new java.lang.StringBuilder()

    private def flushConsoleBuf(): Unit = {
      val maybeLine = consoleBuf.synchronized {
        val i = consoleBuf.indexOf("\n")
        if (i >= 0) {
          val line = consoleBuf.substring(0, i)
          consoleBuf.delete(0, i + 1)
          Some(line)
        } else {
          None
        }
      }

      for (line <- maybeLine) {
        logLine(line)
        flushConsoleBuf()
      }
    }

    private val consoleWriter = new Writer() {
      override def write(chars: Array[Char], offset: Int, length: Int): Unit = {
        consoleBuf.synchronized {
          consoleBuf.append(chars, offset, length);
        }
      }

      override def flush(): Unit = flushConsoleBuf

      override def close(): Unit = flushConsoleBuf
    }

    private val consoleOut = ConsoleLogger.printWriterOut(new PrintWriter(consoleWriter))
  }
}
