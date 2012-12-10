/*
 * Copyright 2012 Typesafe, Inc.
 * Based on sbt IPC code copyright 2009 Mark Harrah
 */

package com.typesafe.sbtchild

import java.net.{ InetAddress, ServerSocket, Socket }
import java.io.DataInputStream
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

object IPC {
  private val loopback = InetAddress.getByName(null)

  private def ignoringIOException[T](block: => T): Unit = {
    try {
      block
    } catch {
      case e: IOException => ()
    }
  }

  private val version = "1"
  private val ServerGreeting = "I am Server: " + version
  private val ClientGreeting = "I am Client: " + version

  private val utf8 = Charset.forName("UTF-8")

  trait Envelope[T] {
    def serial: Long
    def replyTo: Long
    def content: T
  }

  case class WireEnvelope(length: Int, override val serial: Long, override val replyTo: Long, override val content: Array[Byte]) extends Envelope[Array[Byte]] {
    def asString: String = {
      new String(content, utf8)
    }

    def asDeserialized: AnyRef = {
      val inStream = new ByteArrayInputStream(content)
      val inObjectStream = new ObjectInputStream(inStream)
      val o = inObjectStream.readObject()
      inObjectStream.close()
      o
    }
  }

  // This is intended to support reading from one thread
  // while writing from another, but not two threads both
  // reading or both writing concurrently
  abstract class Peer(protected val socket: Socket) {
    private val in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))
    private val out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))

    // this would only be useful if we buffered received messages and
    // allowed replies to be sent out of order
    private var nextSerial = 1L

    protected def handshake(toSend: String, toExpect: String): Unit = {
      sendString(toSend)

      val m = receive()
      if (m.serial != 1L) {
        close()
        throw new IOException("Expected handshake serial 1")
      }

      val s = m.asString
      if (s != toExpect) {
        close()
        throw new IOException("Expected greeting '" + toExpect + "' received '" + s + "'")
      }
    }

    def isClosed = socket.isClosed()

    def send(message: WireEnvelope): Unit = {
      out.writeInt(message.length)
      out.writeLong(message.serial)
      out.writeLong(message.replyTo)
      out.write(message.content)
      out.flush()
    }

    def send(message: Array[Byte]): Long = {
      reply(0L, message)
    }

    def reply(replyTo: Long, message: Array[Byte]): Long = {
      val serial = nextSerial
      nextSerial += 1
      send(WireEnvelope(message.length, serial, replyTo, message))
      serial
    }

    def receive(): WireEnvelope = {
      val length = in.readInt()
      val serial = in.readLong()
      val replyTo = in.readLong()
      val bytes = new Array[Byte](length)
      in.readFully(bytes)
      WireEnvelope(length, serial, replyTo, bytes)
    }

    def sendString(message: String): Long = {
      send(message.getBytes(utf8))
    }

    def replyString(replyTo: Long, message: String): Long = {
      reply(replyTo, message.getBytes(utf8))
    }

    private def toBytes(o: AnyRef): Array[Byte] = {
      val byteStream = new ByteArrayOutputStream()
      val objectStream = new ObjectOutputStream(byteStream)
      objectStream.writeObject(o)
      objectStream.close()
      byteStream.toByteArray()
    }

    def sendSerialized(message: AnyRef): Long = {
      send(toBytes(message))
    }

    def replySerialized(replyTo: Long, message: AnyRef): Long = {
      reply(replyTo, toBytes(message))
    }

    def close(): Unit = {
      ignoringIOException { in.close() }
      ignoringIOException { out.close() }
      ignoringIOException { socket.close() }
    }
  }

  class Server(private val serverSocket: ServerSocket) extends Peer(serverSocket.accept()) {

    handshake(ServerGreeting, ClientGreeting)

    def port = serverSocket.getLocalPort()

    override def close() = {
      super.close()
      ignoringIOException { serverSocket.close() }
    }
  }

  class Client(socket: Socket) extends Peer(socket) {
    handshake(ClientGreeting, ServerGreeting)
  }

  def openServerSocket(): ServerSocket = {
    new ServerSocket(0, 1, loopback)
  }

  def accept(serverSocket: ServerSocket): Server = {
    new Server(serverSocket)
  }

  def openClient(port: Int): Client = {
    new Client(new Socket(loopback, port))
  }

}

object Protocol {

  // These are wire messages on the socket
  sealed trait Message extends Product with Serializable
  sealed trait Request extends Message
  sealed trait Response extends Message
  sealed trait Event extends Message

  case object NameRequest extends Request
  case class NameResponse(name: String) extends Response

  case object CompileRequest extends Request
  case object CompileResponse extends Response

  // can be the response to anything
  case class ErrorResponse(error: String) extends Response

  // pseudo-wire-messages we synthesize locally
  case object Started extends Event
  case object Stopped extends Event

  // should not happen, basically
  case class MysteryMessage(something: Any) extends Message

  case class Envelope(override val serial: Long, override val replyTo: Long, override val content: Message) extends IPC.Envelope[Message]

  object Envelope {
    def apply(wire: IPC.WireEnvelope): Envelope = {
      wire.asDeserialized match {
        case m: Message =>
          Envelope(wire.serial, wire.replyTo, m)
        case other =>
          Envelope(wire.serial, wire.replyTo, MysteryMessage(other))
      }
    }
  }
}
