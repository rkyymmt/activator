package com.typesafe.sbtchild

import akka.actor._
import java.util.concurrent.Executors
import scala.collection.JavaConverters._
import java.io.File
import akka.util.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.io.IOException
import akka.event.Logging
import java.io.InputStream
import java.io.BufferedInputStream

sealed trait ProcessRequest
case class SubscribeProcess(ref: ActorRef) extends ProcessRequest
case class UnsubscribeProcess(ref: ActorRef) extends ProcessRequest
case object StartProcess extends ProcessRequest
case object DestroyProcess extends ProcessRequest

sealed trait ProcessEvent
case class ProcessStdOut(bytes: ByteString) extends ProcessEvent
case class ProcessStdErr(bytes: ByteString) extends ProcessEvent
case class ProcessStopped(exitValue: Int) extends ProcessEvent

class ProcessActor(argv: Seq[String], cwd: File, textMode: Boolean = true) extends Actor with ActorLogging {
  var subscribers: Set[ActorRef] = Set.empty
  var process: Option[Process] = None
  // flag handles the race where we get a destroy request
  // before the process starts
  var destroyRequested = false

  val pool = NamedThreadFactory.newPool("ProcessActor")

  val selfRef = context.self

  override def receive = {
    case req: ProcessRequest => req match {
      case SubscribeProcess(ref) =>
        subscribers = subscribers + ref
        context.watch(ref)
      case UnsubscribeProcess(ref) =>
        subscribers = subscribers - ref
        context.unwatch(ref)
      case StartProcess =>
        if (!destroyRequested)
          start()
      case DestroyProcess =>
        destroyRequested = true
        destroy()
    }

    case Terminated(ref) =>
      subscribers = subscribers - ref

    case event: ProcessEvent =>
      for (s <- subscribers) {
        s ! event
      }

      event match {
        case ProcessStopped(_) =>
          context.stop(self)
        case _ =>
      }

    case p: Process =>
      process = Some(p)
      if (destroyRequested) {
        destroy()
      }
  }

  def start(): Unit = {
    log.debug("Starting process with argv={}", argv)

    pool.execute(new Runnable() {
      override def run = {
        val pb = (new ProcessBuilder(argv.asJava)).directory(cwd)
        val process = pb.start()

        selfRef ! process

        val streamLatch = new CountDownLatch(2)

        def startReader(label: String, rawStream: InputStream, wrap: ByteString => ProcessEvent): Unit = {
          pool.execute(new Runnable() {
            override def run = {
              val stream = new BufferedInputStream(rawStream)
              try {
                var eof = false
                while (!eof) {
                  val bytes = new Array[Byte](256)
                  val count = stream.read(bytes)
                  if (count > 0) {
                    selfRef ! wrap(ByteString.fromArray(bytes, 0, count))
                    log.debug("    sent {} bytes from the child std{}", count, label)
                  } else if (count == -1) {
                    eof = true
                  }
                }
              } catch {
                case e: IOException =>
                  // an expected exception here is "stream closed"
                  // on stream close we end the thread.
                  log.debug("    stream std{} from process closed", label)
              } finally {
                streamLatch.countDown()
                log.debug("    ending std{} reader thread", label)
              }
            }
          })
        }

        startReader("out", process.getInputStream(), ProcessStdOut)
        startReader("err", process.getErrorStream(), ProcessStdErr)

        log.debug("  waiting for process")
        val result = process.waitFor()
        log.debug("  process waited for, waiting to gather any output")
        // try to finish reading out/in before we send ProcessStopped
        streamLatch.await(5000, TimeUnit.MILLISECONDS)

        selfRef ! ProcessStopped(result)

        log.debug("  process thread ending")
      }
    })
  }

  def destroy() = {
    process.foreach({ p =>

      process = None

      log.debug("  stopping process")

      p.destroy()
    })

    if (!pool.isShutdown())
      pool.shutdown()
    if (!pool.isTerminated())
      pool.awaitTermination(2000, TimeUnit.MILLISECONDS)
  }

  override def postStop() = {
    log.debug("postStop")
    destroy()
  }
}

object ProcessActor {
  def apply(system: ActorSystem, name: String, argv: Seq[String], cwd: File): ActorRef = {
    system.actorOf(Props(new ProcessActor(argv, cwd)), name)
  }
}
