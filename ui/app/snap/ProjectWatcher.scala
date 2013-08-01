package snap

import akka.actor._
import scala.concurrent.duration._
import java.io.File
import com.typesafe.sbtrc.EventSourceActor
import java.io.IOException
import com.typesafe.sbtrc.InvalidateSbtBuild
import play.api.libs.json._

sealed trait ProjectWatcherRequest
case object RescanProjectRequest extends ProjectWatcherRequest

sealed trait ProjectWatcherEvent

// for the actual decision to recompile, we use the list of sources
// retrieved from the sbt build, not just everything matching a glob
// (which is what we find here). We use a poll in this actor to find
// added and removed files. File changes are detected elsewhere; changes
// to the build files are found by a child actor of this one, and changes
// to the project sources are found by a child of the app actor.
// So this actor is responsible for:
// - telling the client-side JS to reload sources from sbt
// - telling its child actor to change the set of sbt build sources
// - invalidating the sbt pool when sbt build sources change
class ProjectWatcher(val location: File, val newSourcesSocket: ActorRef, val sbtPool: ActorRef)
  extends EventSourceActor with ActorLogging {

  private val interval = 3.seconds

  case class Contents(projectSources: Set[File], buildSources: Set[File]) {
    def ++(other: Contents): Contents = {
      copy(projectSources = (projectSources ++ other.projectSources),
        buildSources = (buildSources ++ other.buildSources))
    }
  }

  object Contents {
    val empty = Contents(Set.empty, Set.empty)
  }

  private var files: Contents = Contents.empty

  private val timer = {
    implicit val ec = context.dispatcher
    context.system.scheduler.schedule(interval, interval, self, RescanProjectRequest)
  }

  private val sbtBuildWatcher = context.actorOf(Props(new FileWatcher()), name = "sbt-build-file-watcher")

  context.watch(sbtBuildWatcher)
  sbtBuildWatcher ! SubscribeFileChanges(self)

  override def receive = {
    case Terminated(ref) if ref == sbtBuildWatcher =>
      log.debug("sbt build watcher died, so we are too")
      self ! PoisonPill

    case req: ProjectWatcherRequest =>
      req match {
        case RescanProjectRequest =>
          rescan()
      }

    case event: FileWatcherEvent => event match {
      case FilesChanged =>
        // we need to reset all our sbts
        sbtPool ! InvalidateSbtBuild
    }
  }

  private def isSource(f: File): Boolean = {
    val name = f.getName
    name.endsWith(".scala") || name.endsWith(".java")
  }

  private def isSbt(f: File): Boolean = {
    val name = f.getName
    name.endsWith(".sbt")
  }

  private def scanBuildDir(dir: File): Set[File] = {
    (dir.listFiles().toList.map { f =>
      if (isSource(f) || isSbt(f)) {
        Set(f)
      } else if (f.isDirectory()) {
        scanBuildDir(f)
      } else {
        Set.empty[File]
      }
    })
      .fold(Set.empty[File]) { (sofar: Set[File], next: Set[File]) => sofar ++ next }
  }

  // this is inefficient, sue me. if it breaks in practice we can fix it.
  private def scanAnyDir(dir: File): Contents = {
    (dir.listFiles().toList.map { f =>
      if (isSource(f)) {
        Contents(projectSources = Set(f), buildSources = Set.empty)
      } else if (isSbt(f)) {
        Contents(projectSources = Set.empty, buildSources = Set(f))
      } else if (f.isDirectory()) {
        if (f.getName == "project")
          Contents(projectSources = Set.empty, buildSources = scanBuildDir(f))
        else if (f.getName == "target")
          Contents.empty
        else
          scanAnyDir(f)
      } else {
        Contents.empty
      }
    })
      .fold(Contents.empty) { (sofar: Contents, c: Contents) => sofar ++ c }
  }

  private def rescan(): Unit = {
    try {
      val newContents = scanAnyDir(location)
      if (newContents != files) {
        if (newContents.buildSources != files.buildSources) {
          sbtBuildWatcher ! SetFilesToWatch(newContents.buildSources.toSet)
        }
        if (newContents.projectSources != files.projectSources) {
          // notify client to reload sources to watch and pick up new files
          newSourcesSocket ! NotifyWebSocket(JsObject(Seq("type" -> JsString("SourcesMayHaveChanged"))))
        }
        files = newContents
      }
    } catch {
      case e: IOException =>
        log.warning(s"Failed to scan directory $location", e)
    }
  }

  override def postStop(): Unit = {
    log.debug("postStop")
    timer.cancel()
  }
}
