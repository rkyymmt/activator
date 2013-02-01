package com.typesafe.sbtchild.protocol

import com.typesafe.sbtchild.ipc
import scala.util.parsing.json._
import com.typesafe.sbtchild.ipc.JsonReader

sealed trait LogEntry
case class LogSuccess(message: String) extends LogEntry
case class LogTrace(throwableClass: String, throwableMessage: String) extends LogEntry
case class LogMessage(level: Int, message: String) extends LogEntry

object LogEntry {
  implicit object JsonRepresentationOfLogEntry extends ipc.JsonRepresentation[LogEntry] {
    override def toJson(entry: LogEntry): JSONObject = {
      val obj = entry match {
        case LogSuccess(message) =>
          Map("type" -> "success", "message" -> message)
        case LogTrace(klass, message) =>
          Map("type" -> "trace", "class" -> klass, "message" -> message)
        case LogMessage(level, message) =>
          Map("type" -> "message", "level" -> level, "message" -> message)
      }
      JSONObject(obj)
    }

    override def fromJson(j: JSONType): LogEntry = {
      j match {
        case JSONObject(obj) =>
          obj("type") match {
            case "success" => LogSuccess(obj("message").asInstanceOf[String])
            case "trace" => LogTrace(obj("class").asInstanceOf[String], obj("message").asInstanceOf[String])
            case "message" => LogMessage(obj("level").asInstanceOf[Number].intValue, obj("message").asInstanceOf[String])
            case whatever =>
              throw new Exception("unexpected LogEntry type: " + whatever)
          }
        case JSONArray(list) =>
          throw new Exception("not expecting JSONArray: " + list)
      }
    }
  }
}

// These are wire messages on the socket
sealed trait Message {
  // this makes it prettier when writing json by hand e.g. in JavaScript
  private def removeDollar(s: String) = {
    val i = s.lastIndexOf('$')
    if (i >= 0)
      s.substring(0, i)
    else
      s
  }
  // avoiding class.getSimpleName because apparently it's buggy with some
  // Scala name manglings
  private def lastChunk(s: String) = {
    val i = s.lastIndexOf('.')
    if (i >= 0)
      s.substring(i + 1)
    else
      s
  }
  def jsonTypeString = removeDollar(lastChunk(getClass.getName))
}

sealed trait Request extends Message
sealed trait Response extends Message
sealed trait Event extends Message

case object NameRequest extends Request
case class NameResponse(name: String, logs: List[LogEntry]) extends Response

case object CompileRequest extends Request
case class CompileResponse(logs: List[LogEntry]) extends Response

case object RunRequest extends Request
case class RunResponse(logs: List[LogEntry]) extends Response

// can be the response to anything
case class ErrorResponse(error: String, logs: List[LogEntry]) extends Response

// pseudo-wire-messages we synthesize locally
case object Started extends Event
case object Stopped extends Event

// should not happen, basically
case class MysteryMessage(something: Any) extends Event

object Message {
  implicit object JsonRepresentationOfMessage extends ipc.JsonRepresentation[Message] {
    override def toJson(m: Message): JSONObject = {
      // the particular JSON created here is
      // probably bogus, and the use of scala's built-in
      // json stuff is probably also bogus, but it
      // will get us going until we better understand
      // the non-bogus.
      import scala.util.parsing.json._
      val base = Map("type" -> m.jsonTypeString)
      val obj: Map[String, Any] = m match {
        case CompileRequest | NameRequest | RunRequest | Started | Stopped =>
          base
        case NameResponse(name, logs) =>
          base ++ Map("name" -> name, "logs" -> ipc.JsonWriter.toJsonArray(logs))
        case CompileResponse(logs) =>
          base ++ Map("logs" -> ipc.JsonWriter.toJsonArray(logs))
        case RunResponse(logs) =>
          base ++ Map("logs" -> ipc.JsonWriter.toJsonArray(logs))
        case ErrorResponse(error, logs) =>
          base ++ Map("error" -> error, "logs" -> ipc.JsonWriter.toJsonArray(logs))
        case MysteryMessage(something) =>
          base ++ Map("something" -> something.toString)
        case whatever =>
          throw new Exception("Need to implement JSON serialization of: " + whatever)
      }
      JSONObject(obj)
    }

    private def parseLogList(obj: Map[String, Any], key: String): List[LogEntry] = {
      val reader = implicitly[JsonReader[LogEntry]]
      obj(key) match {
        case list: List[_] =>
          list collect {
            case m: Map[_, _] => reader.fromJson(JSONObject(m.asInstanceOf[Map[String, _]]))
          }
        case whatever =>
          throw new Exception("expecting a json array of log entry, got: " + whatever)
      }
    }

    override def fromJson(json: JSONType): Message = {
      json match {
        case JSONObject(obj) =>
          obj("type") match {
            case "NameRequest" =>
              NameRequest
            case "NameResponse" =>
              NameResponse(obj("name").asInstanceOf[String], parseLogList(obj, "logs"))
            case "CompileRequest" =>
              CompileRequest
            case "CompileResponse" =>
              CompileResponse(parseLogList(obj, "logs"))
            case "RunRequest" =>
              RunRequest
            case "RunResponse" =>
              RunResponse(parseLogList(obj, "logs"))
            case "ErrorResponse" =>
              ErrorResponse(obj("error").asInstanceOf[String], parseLogList(obj, "logs"))
            case "Started" =>
              Started
            case "Stopped" =>
              Stopped
            case "MysteryMessage" =>
              MysteryMessage(obj("something").asInstanceOf[String])
            case whatever =>
              throw new Exception("unknown message type in json: " + whatever)
          }
        case JSONArray(list) =>
          throw new Exception("not expecting a json list")
      }
    }
  }
}

case class Envelope(override val serial: Long, override val replyTo: Long, override val content: Message) extends ipc.Envelope[Message]

object Envelope {
  def apply(wire: ipc.WireEnvelope): Envelope = {
    val message = try {
      val json = JSON.parseFull(wire.asString) match {
        case Some(obj: Map[_, _]) => JSONObject(obj.asInstanceOf[Map[String, _]])
        case whatever =>
          throw new Exception("JSON parse failure on: " + wire.asString + " parsed: " + whatever)
      }
      // this can throw malformed json errors
      implicitly[JsonReader[Message]].fromJson(json)
    } catch {
      case e: Exception =>
        // probably a JSON parse failure
        if (wire.replyTo != 0L)
          ErrorResponse(e.getClass.getSimpleName + ": " + e.getMessage, Nil)
        else
          MysteryMessage(try wire.asString catch { case e: Exception => wire })
    }
    Envelope(wire.serial, wire.replyTo, message)
  }
}
