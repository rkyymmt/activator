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

sealed trait AppRequest

case class GetTaskActor(id: String, description: String) extends AppRequest
case object CreateWebSocket extends AppRequest
case class NotifyWebSocket(json: JsValue) extends AppRequest
case object InitialTimeoutExpired extends AppRequest

sealed trait AppReply

case class TaskActorReply(ref: ActorRef) extends AppReply
case object WebSocketAlreadyUsed extends AppReply

class AppActor(val config: AppConfig, val sbtMaker: SbtChildProcessMaker) extends Actor with ActorLogging {

  def location = config.location

  val childFactory = new DefaultSbtChildFactory(location, sbtMaker)
  val sbts = context.actorOf(Props(new ChildPool(childFactory)), name = "sbt-pool")
  val socket = context.actorOf(Props(new AppSocketActor()), name = "socket")

  var webSocketCreated = false

  context.watch(sbts)
  context.watch(socket)

  // we can stay alive due to socket connection (and then die with the socket)
  // or else we just die after being around a short time
  context.system.scheduler.scheduleOnce(2.minutes, self, InitialTimeoutExpired)

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def receive = {
    case Terminated(ref) =>
      if (ref == sbts) {
        log.info("sbt pool terminated, killing AppActor")
        self ! PoisonPill
      } else if (ref == socket) {
        log.info("socket terminated, killing AppActor")
        self ! PoisonPill
      } else {
        log.warning("other actor terminated (why are we watching it?) {}", ref)
      }

    case req: AppRequest => req match {
      case GetTaskActor(taskId, description) =>
        sender ! TaskActorReply(context.actorOf(Props(new ChildTaskActor(taskId, description, sbts)),
          name = "task-" + URLEncoder.encode(taskId, "UTF-8")))
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
        socket.forward(notify)
      case InitialTimeoutExpired =>
        if (!webSocketCreated) {
          log.warning("Nobody every connected to {}, killing it", config.id)
          self ! PoisonPill
        }
    }
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

    override def receive = gettingReservation(Nil)

    private def gettingReservation(requestQueue: List[(ActorRef, protocol.Request)]): Receive = {
      case req: protocol.Request => context.become(gettingReservation((sender, req) :: requestQueue))
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
      case Terminated(ref) =>
        log.debug("sbt actor died, task actor self-destructing")
        self ! PoisonPill // our sbt died
    }
  }

  class AppSocketActor extends WebSocketActor[JsValue] with ActorLogging {
    override def onMessage(json: JsValue): Unit = {
      log.info("received message on web socket: {}", json)
    }

    override def subReceive: Receive = {
      case NotifyWebSocket(json) =>
        log.info("sending message on web socket: {}", json)
        produce(json)
    }

    override def postStop(): Unit = {
      log.debug("stopping")
    }
  }
}
