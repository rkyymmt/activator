package com.typesafe.sbtchild.ipc

import java.net.{ InetAddress, ServerSocket, Socket }
import java.io.DataInputStream
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.InputStream
import java.io.ObjectStreamClass

trait Envelope[T] {
  def serial: Long
  def replyTo: Long
  def content: T
}

case class WireEnvelope(length: Int, override val serial: Long, override val replyTo: Long, override val content: Array[Byte]) extends Envelope[Array[Byte]] {
  def asString: String = {
    new String(content, utf8)
  }

  /**
   * @author Guy Oliver
   * This is a hackaround for https://github.com/harrah/xsbt/issues/136 insanity,
   * copied from scala actors remoting code
   */
  private class CustomObjectInputStream(in: InputStream, cl: ClassLoader)
    extends ObjectInputStream(in) {
    override def resolveClass(cd: ObjectStreamClass): Class[_] =
      try {
        cl.loadClass(cd.getName())
      } catch {
        case cnf: ClassNotFoundException =>
          super.resolveClass(cd)
      }
    override def resolveProxyClass(interfaces: Array[String]): Class[_] =
      try {
        val ifaces = interfaces map { iface => cl.loadClass(iface) }
        java.lang.reflect.Proxy.getProxyClass(cl, ifaces: _*)
      } catch {
        case e: ClassNotFoundException =>
          super.resolveProxyClass(interfaces)
      }
  }

  def asDeserialized: AnyRef = {
    val inStream = new ByteArrayInputStream(content)
    val inObjectStream = new CustomObjectInputStream(inStream, this.getClass.getClassLoader())
    val o = inObjectStream.readObject()
    inObjectStream.close()
    o
  }
}

// This is intended to support reading from one thread
// while writing from another, but not two threads both
// reading or both writing concurrently
abstract class Peer(protected val socket: Socket) {
  require(!socket.isClosed())
  require(socket.getInputStream() ne null)
  require(socket.getOutputStream() ne null)

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
    if (isClosed)
      throw new IOException("socket is closed")
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
    if (isClosed)
      throw new IOException("socket is closed")
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
