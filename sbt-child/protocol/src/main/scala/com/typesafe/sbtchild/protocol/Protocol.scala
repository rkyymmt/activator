package com.typesafe.sbtchild.protocol

import com.typesafe.sbtchild.ipc

sealed trait LogEntry extends Serializable
case class LogSuccess(message: String) extends LogEntry
case class LogTrace(throwableClass: String, throwableMessage: String) extends LogEntry
case class LogMessage(level: Int, message: String) extends LogEntry

// These are wire messages on the socket
sealed trait Message extends Serializable
sealed trait Request extends Message
sealed trait Response extends Message
sealed trait Event extends Message

case object NameRequest extends Request
case class NameResponse(name: String, logs: List[LogEntry]) extends Response

case object CompileRequest extends Request
case class CompileResponse(logs: List[LogEntry]) extends Response

// can be the response to anything
case class ErrorResponse(error: String, logs: List[LogEntry]) extends Response

// pseudo-wire-messages we synthesize locally
case object Started extends Event
case object Stopped extends Event

// should not happen, basically
case class MysteryMessage(something: Any) extends Message

case class Envelope(override val serial: Long, override val replyTo: Long, override val content: Message) extends ipc.Envelope[Message]

object Envelope {
  def apply(wire: ipc.WireEnvelope): Envelope = {
    wire.asDeserialized match {
      case m: Message =>
        Envelope(wire.serial, wire.replyTo, m)
      case other =>
        Envelope(wire.serial, wire.replyTo, MysteryMessage(other))
    }
  }
}
