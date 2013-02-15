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

  private def extractWithRef(state: State): (Extracted, ProjectRef) = {
    val ref = Project.extract(state).currentRef
    (Extracted(Project.structure(state), Project.session(state), ref)(Project.showFullKey), ref)
  }

  private def extract(state: State): Extracted = {
    extractWithRef(state)._1
  }

  private def runInputTask[T](key: ScopedKey[T], state: State): State = {
    val extracted = extract(state)
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

    // each test group is in its own thread so this has to be
    // synchronized or we'll stomp on our mutable state and
    // try to use "client" from multiple threads at once
    override def testEvent(event: TestEvent): Unit = synchronized {
      // event.result is just all the detail results folded,
      // we replicate that ourselves below
      for (detail <- event.detail) {
        val outcome = detail.result match {
          case TResult.Success => protocol.TestPassed
          case TResult.Error => protocol.TestError
          case TResult.Failure => protocol.TestFailed
          case TResult.Skipped => protocol.TestSkipped
        }

        overallOutcome = overallOutcome.combine(outcome)

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

  private def handleRequest(req: protocol.Envelope, origState: State, logger: CaptureLogger): State = {
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
      case protocol.Envelope(serial, replyTo, protocol.CompileRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val (s, result) = extract(origState).runTask(compile in Compile, origState)
          client.replyJson(serial, protocol.CompileResponse(success = true))
          s
        }
      case protocol.Envelope(serial, replyTo, protocol.RunRequest(_)) =>
        exceptionsToErrorResponse(serial) {
          val s = runInputTask(run in Compile, origState)
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
