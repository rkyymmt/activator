/*
 * Copyright 2012 Typesafe, Inc.
 * Based on sbt IPC code copyright 2009 Mark Harrah
 */

package com.typesafe.sbtchild

import java.io.IOException
import java.net.{ InetAddress, ServerSocket, Socket }

object Protocol {
  private val loopback = InetAddress.getByName(null)

  class Server(socket: ServerSocket) {
    //    val in = new DataInputStream(socket.getInputStream())
  }

  class Client(socket: Socket) {

  }

  // ops .accept, .close, .getLocalPort, .getInputStream, .getOutputStream
  def openServer(): Server = {
    new Server(new ServerSocket(0, 1, loopback))
  }

  def openClient(port: Int): Client = {
    new Client(new Socket(loopback, port))
  }

}

//	private val in = new BufferedReader(new InputStreamReader(s.getInputStream))
//	private val out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream))

//	def send(s: String) = { out.write(s); out.newLine(); out.flush() }
//	def receive: String = in.readLine()

