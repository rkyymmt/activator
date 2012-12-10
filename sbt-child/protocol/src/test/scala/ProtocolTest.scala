/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
import org.junit.Assert._
import org.junit._
import com.typesafe.sbtchild._
import java.util.concurrent.Executors
import com.typesafe.sbtchild.Protocol._
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProtocolTest {

  private def testClientServer(clientBlock: (IPC.Client) => Unit,
    serverBlock: (IPC.Server) => Unit) = {
    val executor = Executors.newCachedThreadPool()
    val serverSocket = IPC.openServerSocket()
    val port = serverSocket.getLocalPort()
    val latch = new CountDownLatch(2)

    var fail: Option[Exception] = None
    var ok = false

    executor.execute(new Runnable() {
      override def run() = {
        try {
          val server = IPC.accept(serverSocket)

          try {
            serverBlock(server)
          } finally {
            server.close()
          }

          ok = true

        } catch {
          case e: Exception => fail = Some(e)
        } finally {
          latch.countDown()
        }
      }
    })

    executor.execute(new Runnable() {
      override def run() = {
        try {
          val client = IPC.openClient(port)

          try {
            clientBlock(client)
          } finally {
            client.close()
          }

        } catch {
          case e: Exception => fail = Some(e)
        } finally {
          latch.countDown()
        }
      }
    })

    latch.await()

    executor.shutdown()
    executor.awaitTermination(1000, TimeUnit.MILLISECONDS)

    fail foreach { e => throw e }

    assertTrue(ok)
  }

  @Test
  def testRetrieveProjectName(): Unit = {
    testClientServer(
      { (client) =>
        Protocol.Envelope(client.receive()) match {
          case Protocol.Envelope(serial, replyTo, NameRequest) =>
            client.replySerialized(serial, NameResponse("foobar"))
          case Protocol.Envelope(serial, replyTo, other) =>
            client.replySerialized(serial, ErrorResponse("did not understand request: " + other))
        }
      },
      { (server) =>
        server.sendSerialized(NameRequest)
        val name = Protocol.Envelope(server.receive()) match {
          case Protocol.Envelope(serial, replyTo, r: NameResponse) => r.name
          case Protocol.Envelope(serial, replyTo, r) =>
            throw new AssertionError("unexpected response: " + r)
        }
        assertEquals("foobar", name)
      })
  }
}
