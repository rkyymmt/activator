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
import org.scalatools.testing.{ Logger => _, Result => TResult, _ }
import java.net.SocketException
import java.io.EOFException
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import scala.util.matching.Regex

object SetupSbtChild extends (State => State) {

  private lazy val client = ipc.openClient(getPort())

  val ListenCommandName = "listen"

  // this is the entry point invoked by sbt
  override def apply(s: State): State = {
    val betweenRequestsLogger = new EventLogger(client, 0L)
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

  private def getPort(): Int = {
    val portString = System.getProperty("snap.sbt-child-port")
    if (portString == null)
      throw new Exception("No port property set")
    val port = Integer.parseInt(portString)
    port
  }

  private def extractWithRef(state: State): (Extracted, ProjectRef) = {
    val ref = Project.extract(state).currentRef
    (Extracted(Project.structure(state), Project.session(state), ref)(Project.showFullKey), ref)
  }

  private def extract(state: State): Extracted = {
    extractWithRef(state)._1
  }

  private def runInputTask[T](key: ScopedKey[T], state: State, args: String): State = {
    val extracted = extract(state)
    implicit val display = Project.showContextKey(state)
    val it = extracted.get(SettingKey(key.key) in key.scope)
    val keyValues = KeyValue(key, it) :: Nil
    val parser = Aggregation.evaluatingParser(state, extracted.structure, show = false)(keyValues)
    // we put a space in front of the args because the parsers expect
    // *everything* after the task name it seems
    DefaultParsers.parse(" " + args, parser) match {
      case Left(message) =>
        throw new Exception("Failed to run task: " + display(key) + ": " + message)
      case Right(f) =>
        f()
    }
  }

  private def makeAppendSettings(settings: Seq[Setting[_]], inProject: ProjectRef, extracted: Extracted) = {
    // transforms This scopes in 'settings' to be the desired project
    val appendSettings = Load.transformSettings(Load.projectScope(inProject), inProject.build, extracted.rootProject, settings)
    appendSettings
  }

  private def reloadWithAppended(state: State, appendSettings: Seq[Setting[_]]): State = {
    val session = Project.session(state)
    val structure = Project.structure(state)
    implicit val display = Project.showContextKey(state)

    // reloads with appended settings
    val newStructure = Load.reapply(session.original ++ appendSettings, structure)

    // updates various aspects of State based on the new settings
    // and returns the updated State
    Project.setProject(session, newStructure, state)
  }

  private class OurTestListener(val serial: Long, val oldTask: Task[Seq[TestReportListener]]) extends TestReportListener {

    override def startGroup(name: String): Unit = {}

    var overallOutcome: protocol.TestOutcome = protocol.TestPassed

    override def testEvent(event: TestEvent): Unit = {
      // event.result is just all the detail results folded,
      // we replicate that ourselves below
      for (detail <- event.detail) {
        val outcome = detail.result match {
          case TResult.Success => protocol.TestPassed
          case TResult.Error => protocol.TestError
          case TResult.Failure => protocol.TestFailed
          case TResult.Skipped => protocol.TestSkipped
        }

        // each test group is in its own thread so this has to be
        // synchronized
        synchronized {
          overallOutcome = overallOutcome.combine(outcome)
        }

        client.replyJson(serial,
          protocol.TestEvent(detail.testName,
            Option(detail.description),
            outcome,
            Option(detail.error).map(_.getMessage)))
      }
    }

    override def endGroup(name: String, t: Throwable): Unit = {}

    override def endGroup(name: String, result: TestResult.Value): Unit = {}

    override def contentLogger(test: TestDefinition): Option[ContentLogger] = None
  }

  private val listenersKey = testListeners in Test

  private def addTestListener(state: State, serial: Long): State = {
    val (extracted, ref) = extractWithRef(state)
    val ourListener = new OurTestListener(serial, extracted.get(listenersKey))

    val settings = makeAppendSettings(Seq(listenersKey <<= (listenersKey) map { listeners =>
      listeners :+ ourListener
    }), ref, extracted)

    reloadWithAppended(state, settings)
  }

  private def removeTestListener(state: State, serial: Long): (State, protocol.TestOutcome) = {
    val ref = Project.extract(state).currentRef
    val extracted = Extracted(Project.structure(state), Project.session(state), ref)(Project.showFullKey)

    val (s1, listeners) = extracted.runTask(listenersKey, state)

    val ours = listeners.flatMap({
      case l: OurTestListener if l.serial == serial => Seq(l)
      case whatever => Seq.empty[OurTestListener]
    }).headOption
      .getOrElse(throw new RuntimeException("Our test listener wasn't installed!"))

    // put back the original listener task
    val settings = makeAppendSettings(Seq(Project.setting(listenersKey, Project.value(ours.oldTask))), ref, extracted)

    (reloadWithAppended(s1, settings), ours.overallOutcome)
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
      case protocol.Envelope(serial, replyTo, protocol.NameRequest(_)) =>
        val result = extract(origState).get(name)
        client.replyJson(serial, protocol.NameResponse(result))
        origState
      case protocol.Envelope(serial, replyTo, protocol.DiscoveredMainClassesRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val (s, result) = extract(origState).runTask(discoveredMainClasses in Compile in run, origState)
          client.replyJson(serial, protocol.DiscoveredMainClassesResponse(names = result))
          s
        }
      case protocol.Envelope(serial, replyTo, protocol.CompileRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val (s, result) = extract(origState).runTask(compile in Compile, origState)
          client.replyJson(serial, protocol.CompileResponse(success = true))
          s
        }
      case protocol.Envelope(serial, replyTo, protocol.RunRequest(_, mainClass)) =>
        exceptionsToErrorResponse(serial) {
          val s = mainClass map { klass =>
            runInputTask(runMain in Compile, origState, args = klass)
          } getOrElse {
            runInputTask(run in Compile, origState, args = "")
          }
          client.replyJson(serial, protocol.RunResponse(success = true))
          s
        }
      case protocol.Envelope(serial, replyTo, protocol.TestRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val s1 = addTestListener(origState, serial)
          val (s2, result1) = extract(s1).runTask(test in Test, s1)
          val (s3, outcome) = removeTestListener(s2, serial)
          client.replyJson(serial, protocol.TestResponse(outcome))
          s3
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
