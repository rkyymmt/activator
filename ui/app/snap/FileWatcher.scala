package snap

import akka.actor._
import scala.concurrent.duration._
import java.io.File
import com.typesafe.sbtchild.EventSourceActor
import scala.annotation.tailrec
import java.io.IOException

sealed trait FileWatcherRequest
case class SetFilesToWatch(files: Set[File]) extends FileWatcherRequest
case class SubscribeFileChanges(ref: ActorRef) extends FileWatcherRequest
case class UnsubscribeFileChanges(ref: ActorRef) extends FileWatcherRequest
case object CheckTimeoutExpired extends FileWatcherRequest

sealed trait FileWatcherEvent
case object FilesChanged extends FileWatcherEvent

class FileWatcher extends EventSourceActor with ActorLogging {

  private val interval = 1.second

  // map from File to last-modified
  private var files = Map.empty[File, Long]

  override def receive = {
    case req: FileWatcherRequest =>
      req match {
        case SetFilesToWatch(replacements) => setFiles(replacements)
        case SubscribeFileChanges(ref) => subscribe(ref)
        case UnsubscribeFileChanges(ref) => unsubscribe(ref)
        case CheckTimeoutExpired => check()
      }
  }

  // invariant: we have a timeout scheduled if and only if
  // files.nonEmpty
  private def reschedule(): Unit = {
    implicit val ec = context.dispatcher
    context.system.scheduler.scheduleOnce(interval, self, CheckTimeoutExpired)
  }

  private def setFiles(replacements: Set[File]): Unit = {
    val wasEmpty = files.isEmpty
    val changed = replacements != files.keySet
    if (changed) {
      files = (replacements.map { f =>
        (f -> files.get(f).getOrElse(try f.lastModified() catch { case e: IOException => 0L }))
      }).toMap

      if (wasEmpty && files.nonEmpty)
        reschedule()

      log.debug(s"Set of files has been modified, now watching ${files.size} files")
      emitEvent(FilesChanged)
    } else {
      log.debug(s"Same ${replacements.size} files sent to FileWatcher, doing nothing")
    }
  }

  private def check(): Unit = {
    // files will be empty on the last timeout after
    // we set the file set to the empty set
    if (files.nonEmpty) {
      val withOldAndNew = files.toList map {
        case (f, oldTime) =>
          val newTime = f.lastModified()
          (f, oldTime, newTime)
      }

      val changed = withOldAndNew.foldLeft(false) { (sofar, triple) => sofar || triple._2 != triple._3 }
      files = (withOldAndNew.map { triple => (triple._1 -> triple._3) }).toMap

      if (changed) {
        log.debug("File changes detected")
        emitEvent(FilesChanged)
      }

      reschedule()
    }
  }
}
