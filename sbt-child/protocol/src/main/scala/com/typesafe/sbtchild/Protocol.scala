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

  abstract class Peer(protected val socket: Socket) {
    private val in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))
    private val out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))

    private val utf8 = Charset.forName("UTF-8")

    // this would only be useful if we buffered received messages and
    // allowed replies to be sent out of order
    private var nextSerial = 1L

    case class Message(length: Int, serial: Long, replyTo: Long, body: Array[Byte]) {
      def asString: String = {
        new String(body, utf8)
      }
    }

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

    def send(message: Message): Unit = {
      out.writeInt(message.length)
      out.writeLong(message.serial)
      out.writeLong(message.replyTo)
      out.write(message.body)
      out.flush()
    }

    def send(message: Array[Byte]): Unit = {
      reply(0L, message)
    }

    def reply(replyTo: Long, message: Array[Byte]): Unit = {
      send(Message(message.length, nextSerial, replyTo, message))
      nextSerial += 1
    }

    def receive(): Message = {
      val length = in.readInt()
      val serial = in.readLong()
      val replyTo = in.readLong()
      val bytes = new Array[Byte](length)
      in.readFully(bytes)
      val m = Message(length, serial, replyTo, bytes)
      m
    }

    def sendString(message: String): Unit = {
      send(message.getBytes(utf8))
    }

    def replyString(replyTo: Long, message: String): Unit = {
      reply(replyTo, message.getBytes(utf8))
    }

    def receiveString(): String = {
      receive().asString
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

  sealed trait Tagged {
    def tag: String
    def serial: Long
  }

  case class Name(override val serial: Long) extends Tagged {
    override def tag = Name.tag
  }

  object Name {
    val tag = "name"
  }

  class ClientOps(client: IPC.Client) {
    def receiveRequest(): Tagged = {
      val message = client.receive()
      val body = message.asString
      body match {
        case Name.tag =>
          Name(message.serial)
      }
    }
    def replyName(replyTo: Long, name: String) = {
      client.replyString(replyTo, name)
    }
  }

  class ServerOps(server: IPC.Server) {
    def requestName(): Unit = {
      server.sendString(Name.tag)
    }

    def receiveName(): String = {
      server.receiveString()
    }
  }

  implicit def client2ops(client: IPC.Client) = new ClientOps(client)

  implicit def server2ops(server: IPC.Server) = new ServerOps(server)

}
