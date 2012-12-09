package com.typesafe.sbtchild

import akka.actor._
import java.util.concurrent.Executors
import scala.collection.JavaConverters._
import java.io.File
import akka.util.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

sealed trait ProcessRequest
case class Subscribe(ref: ActorRef) extends ProcessRequest
case class Unsubscribe(ref: ActorRef) extends ProcessRequest
case object StartProcess extends ProcessRequest
case object DestroyProcess extends ProcessRequest

sealed trait ProcessEvent
case class ProcessStdOut(bytes: ByteString) extends ProcessEvent
case class ProcessStdErr(bytes: ByteString) extends ProcessEvent
case class ProcessStopped(exitValue: Int) extends ProcessEvent

class ProcessActor(argv: Seq[String], cwd: File, textMode: Boolean = true) extends Actor {
  var subscribers: Set[ActorRef] = Set.empty
  var process: Option[Process] = None
  // flag handles the race where we get a destroy request
  // before the process starts
  var destroyRequested = false

  val pool = Executors.newCachedThreadPool()

  val selfRef = context.self

  override def receive = {
    case req: ProcessRequest => req match {
      case Subscribe(ref) =>
        subscribers = subscribers + ref
        context.watch(ref)
      case Unsubscribe(ref) =>
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
    pool.execute(new Runnable() {
      override def run = {
        val pb = (new ProcessBuilder(argv.asJava)).directory(cwd)
        val process = pb.start()

        selfRef ! process

        val out = process.getInputStream()
        val err = process.getErrorStream()

        val streamLatch = new CountDownLatch(2)

        def startReader(wrap: ByteString => ProcessEvent): Unit = {
          pool.execute(new Runnable() {
            override def run = {
              try {
                var eof = false
                while (!eof) {
                  val bytes = new Array[Byte](256)
                  val count = out.read(bytes)
                  if (count > 0) {
                    selfRef ! wrap(ByteString.fromArray(bytes, 0, count))
                  } else if (count == -1) {
                    eof = true
                  }
                }
              } finally {
                streamLatch.countDown()
              }
            }
          })
        }

        startReader(ProcessStdOut)
        startReader(ProcessStdErr)

        val result = process.waitFor()
        // try to finish reading out/in before we send ProcessStopped
        streamLatch.await(5000, TimeUnit.MILLISECONDS)

        selfRef ! ProcessStopped(result)
      }
    })
  }

  def destroy() = {
    process.foreach({ p =>

      process = None

      p.destroy()
    })

    if (!pool.isShutdown())
      pool.shutdown()
    if (!pool.isTerminated())
      pool.awaitTermination(2000, TimeUnit.MILLISECONDS)
  }

  override def postStop() = {
    destroy()
  }
}

object ProcessActor {
  def apply(system: ActorSystem, name: String, argv: Seq[String], cwd: File): ActorRef = {
    system.actorOf(Props(new ProcessActor(argv, cwd)), name)
  }
}
