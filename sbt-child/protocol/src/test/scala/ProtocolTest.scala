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
  @Test
  def exchangeMessages() {
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
          server.requestName()
          val name = server.receiveName()

          assertEquals("foobar", name)

          server.close()

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
          client.receiveRequest() match {
            case Name(replyTo) =>
              client.replyName(replyTo, "foobar")
          }
        } catch {
          case e: Exception => fail = Some(e)
        } finally {
          latch.countDown()
        }
      }
    })

    executor.shutdown()
    executor.awaitTermination(1000, TimeUnit.MILLISECONDS)

    fail foreach { e => throw e }

    assertTrue(ok)
  }
}
