package snap

import com.typesafe.sbtchild._
import akka.actor._
import java.io.File
import java.util.UUID
import play.api.libs.json.JsValue
import java.net.URLEncoder
import akka.pattern._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._
import play.api.libs.json._

sealed trait AppRequest

case class GetTaskActor(id: String, description: String) extends AppRequest
case object CreateWebSocket extends AppRequest
case class NotifyWebSocket(json: JsObject) extends AppRequest
case object InitialTimeoutExpired extends AppRequest
case class ForceStopTask(id: String) extends AppRequest
case class UpdateSourceFiles(files: Set[File]) extends AppRequest

sealed trait AppReply

case class TaskActorReply(ref: ActorRef) extends AppReply
case object WebSocketAlreadyUsed extends AppReply

class AppActor(val config: AppConfig, val sbtMaker: SbtChildProcessMaker) extends Actor with ActorLogging {

  AppManager.registerKeepAlive(self)

  def location = config.location

  val childFactory = new DefaultSbtChildFactory(location, sbtMaker)
  val sbts = context.actorOf(Props(new ChildPool(childFactory)), name = "sbt-pool")
  val socket = context.actorOf(Props(new AppSocketActor()), name = "socket")
  val watcher = context.actorOf(Props(new FileWatcher()), name = "watcher")

  var webSocketCreated = false

  var tasks = Map.empty[String, ActorRef]

  context.watch(sbts)
  context.watch(socket)
  context.watch(watcher)

  // we can stay alive due to socket connection (and then die with the socket)
  // or else we just die after being around a short time
  context.system.scheduler.scheduleOnce(2.minutes, self, InitialTimeoutExpired)

  // get file watch notifications
  watcher ! SubscribeFileChanges(self)

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def receive = {
    case Terminated(ref) =>
      if (ref == sbts) {
        log.info("sbt pool terminated, killing AppActor")
        self ! PoisonPill
      } else if (ref == socket) {
        log.info("socket terminated, killing AppActor")
        self ! PoisonPill
      } else if (ref == watcher) {
        log.info("watcher terminated, killing AppActor")
        self ! PoisonPill
      } else {
        tasks.find { kv => kv._2 == ref } match {
          case Some((taskId, task)) =>
            log.debug("forgetting terminated task {} {}", taskId, task)
            tasks -= taskId
          case None =>
            log.warning("other actor terminated (why are we watching it?) {}", ref)
        }
      }

    case req: AppRequest => req match {
      case GetTaskActor(taskId, description) =>
        val task = context.actorOf(Props(new ChildTaskActor(taskId, description, sbts)),
          name = "task-" + URLEncoder.encode(taskId, "UTF-8"))
        tasks += (taskId -> task)
        context.watch(task)
        log.debug("created task {} {}", taskId, task)
        sender ! TaskActorReply(task)
      case CreateWebSocket =>
        log.debug("got CreateWebSocket")
        if (webSocketCreated) {
          log.warning("Attempt to create websocket for app a second time {}", config.id)
          sender ! WebSocketAlreadyUsed
        } else {
          webSocketCreated = true
          socket.tell(GetWebSocket, sender)
        }
      case notify: NotifyWebSocket =>
        if (validateEvent(notify.json)) {
          socket.forward(notify)
        } else {
          log.error("Attempt to send invalid event {}", notify.json)
        }
      case InitialTimeoutExpired =>
        if (!webSocketCreated) {
          log.warning("Nobody every connected to {}, killing it", config.id)
          self ! PoisonPill
        }
      case ForceStopTask(id) =>
        tasks.get(id).foreach { ref =>
          log.debug("ForceStopTask for {} sending stop to {}", id, ref)
          ref ! ForceStop
        }
      case UpdateSourceFiles(files) =>
        watcher ! SetFilesToWatch(files)
    }

    case event: FileWatcherEvent => event match {
      case FilesChanged =>
        socket ! NotifyWebSocket(JsObject(Seq("type" -> JsString("FilesChanged"))))
    }
  }

  private def validateEvent(json: JsObject): Boolean = {
    // we need either a toplevel "type" or a toplevel "taskId"
    // and then a nested "event" with a "type"
    val hasType = json \ "type" match {
      case JsString(t) => true
      case _ => false
    }
    val hasTaskId = json \ "taskId" match {
      case JsString(t) =>
        json \ "event" \ "type" match {
          case JsString(t) => true
          case _ => false
        }
      case _ => false
    }
    hasType || hasTaskId;
  }

  // this actor corresponds to one protocol.Request, and any
  // protocol.Event that are associated with said request.
  // This is spawned from ChildTaskActor for each request.
  class ChildRequestActor(val requestor: ActorRef, val sbt: ActorRef, val request: protocol.Request) extends Actor with ActorLogging {
    sbt ! request

    override def receive = {
      case response: protocol.Response =>
        requestor.forward(response)
        // Response is supposed to arrive at the end,
        // after all Event
        log.debug("request responded to, request actor self-destructing")
        self ! PoisonPill
      case event: protocol.Event =>
        requestor.forward(event)
    }
  }

  private sealed trait ChildTaskRequest
  private case object ForceStop extends ChildTaskRequest

  // this actor's lifetime corresponds to one sequence of interactions with
  // an sbt instance obtained from the sbt pool.
  // It gets the pool from the app; reserves an sbt in the pool; and
  // forwards any messages you like to that pool.
  class ChildTaskActor(val taskId: String, val taskDescription: String, val pool: ActorRef) extends Actor {

    val reservation = SbtReservation(id = taskId, taskName = taskDescription)

    var requestSerial = 0
    def nextRequestName() = {
      requestSerial += 1
      "subtask-" + requestSerial
    }

    pool ! RequestAnSbt(reservation)

    private def handleRequest(requestor: ActorRef, sbt: ActorRef, request: protocol.Request) = {
      context.actorOf(Props(new ChildRequestActor(requestor = requestor,
        sbt = sbt, request = request)), name = nextRequestName())
    }

    private def errorOnStopped(requestor: ActorRef, request: protocol.Request) = {
      requestor ! protocol.ErrorResponse(s"Task has been stopped (task ${reservation.id} request ${request})")
    }

    private def handleTerminated(ref: ActorRef, sbtOption: Option[ActorRef]): Unit = {
      if (Some(ref) == sbtOption) {
        log.debug("sbt actor died, task actor self-destructing")
        self ! PoisonPill // our sbt died
      }
    }

    override def receive = gettingReservation(Nil)

    private def gettingReservation(requestQueue: List[(ActorRef, protocol.Request)]): Receive = {
      case req: ChildTaskRequest => req match {
        case ForceStop =>
          pool ! ForceStopAnSbt(reservation.id) // drops our reservation
          requestQueue.reverse.foreach(tuple => errorOnStopped(tuple._1, tuple._2))
          context.become(forceStopped(None))
      }
      case req: protocol.Request =>
        context.become(gettingReservation((sender, req) :: requestQueue))
      case SbtGranted(filled) =>
        val sbt = filled.sbt.getOrElse(throw new RuntimeException("we were granted a reservation with no sbt"))
        // send the queue
        requestQueue.reverse.foreach(tuple => handleRequest(tuple._1, sbt, tuple._2))

        // monitor sbt death
        context.watch(sbt)
        // now enter have-sbt mode
        context.become(haveSbt(sbt))

      // when we die, the reservation should be auto-released by ChildPool
    }

    private def haveSbt(sbt: ActorRef): Receive = {
      case req: protocol.Request => handleRequest(sender, sbt, req)
      case ForceStop => {
        pool ! ForceStopAnSbt(reservation.id)
        context.become(forceStopped(Some(sbt)))
      }
      case Terminated(ref) => handleTerminated(ref, Some(sbt))
    }

    private def forceStopped(sbtOption: Option[ActorRef]): Receive = {
      case req: protocol.Request => errorOnStopped(sender, req)
      case Terminated(ref) => handleTerminated(ref, sbtOption)
      case SbtGranted(filled) =>
        pool ! ReleaseAnSbt(reservation.id)
    }
  }

  class AppSocketActor extends WebSocketActor[JsValue] with ActorLogging {
    override def onMessage(json: JsValue): Unit = {
      json match {
        case WebSocketActor.Ping(ping) => produce(WebSocketActor.Pong(ping.cookie))
        case _ => log.info("unhandled message on web socket: {}", json)
      }
    }

    override def subReceive: Receive = {
      case NotifyWebSocket(json) =>
        log.debug("sending message on web socket: {}", json)
        produce(json)
    }

    override def postStop(): Unit = {
      log.debug("postStop")
    }
  }
}
