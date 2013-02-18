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
import java.util.concurrent.RejectedExecutionException

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

  // counts down when we've read stdout and stderr
  val gotOutputLatch = new CountDownLatch(2)

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

    val pb = (new ProcessBuilder(argv.asJava)).directory(cwd)
    val process = pb.start()

    // we don't want the process to block on stdin.
    // redirecting stdin from /dev/null would be nicer than
    // closing it, but Java doesn't seem to have a way to do that.
    loggingFailure(log) { process.getOutputStream().close() }

    selfRef ! process

    def startReader(label: String, rawStream: InputStream, wrap: ByteString => ProcessEvent): Unit = try {
      pool.execute(new Runnable() {
        override def run = {
          val stream = new BufferedInputStream(rawStream)
          try {
            val bytes = new Array[Byte](4096)
            var eof = false
            while (!eof) {
              val count = stream.read(bytes)
              if (count > 0) {
                selfRef ! wrap(ByteString.fromArray(bytes, 0, count))
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
            gotOutputLatch.countDown()
            log.debug("    ending std{} reader thread", label)
            try stream.close() catch { case e: IOException => }
          }
        }
      })
    } catch {
      case e: RejectedExecutionException =>
        log.warning("thread pool destroyed before we could read std" + label)
    }

    startReader("out", process.getInputStream(), ProcessStdOut)
    startReader("err", process.getErrorStream(), ProcessStdErr)

    def collectProcess(): Unit = {
      log.debug("  waiting for process")
      val result = process.waitFor()
      log.debug("  process waited for, waiting to gather any output")
      // try to finish reading out/in before we send ProcessStopped
      gotOutputLatch.await(5000, TimeUnit.MILLISECONDS)

      selfRef ! ProcessStopped(result)
    }

    try {
      pool.execute(new Runnable() {
        override def run = {
          log.debug("  process thread starting")
          collectProcess()
          log.debug("  process thread ending")
        }
      })
    } catch {
      case e: RejectedExecutionException =>
        log.warning("thread pool destroyed before we could wait for process")
        // at this point the process should be dead so this shouldn't block long
        collectProcess()
    }
  }

  def destroy() = {
    process.foreach({ p =>

      process = None

      log.debug("  stopping process")

      p.destroy()

      // briefly pause if we haven't read the stdout/stderr yet,
      // to just have a chance to get them
      gotOutputLatch.await(2000, TimeUnit.MILLISECONDS)
    })

    // if we haven't started up the stdout/err reader or waitFor threads
    // yet, at this point they would get RejectedExecutionException
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
