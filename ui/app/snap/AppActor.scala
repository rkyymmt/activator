package snap

import com.typesafe.sbtchild._
import akka.actor._
import java.io.File
import java.util.UUID

sealed trait AppRequest

case class GetTaskActor(description: String) extends AppRequest

sealed trait AppReply

case class TaskActorReply(ref: ActorRef) extends AppReply

class AppActor(val location: File, val sbtMaker: SbtChildProcessMaker) extends Actor {

  val childFactory = new DefaultSbtChildFactory(location, sbtMaker)
  val sbts = context.actorOf(Props(new ChildPool(childFactory)))

  override def receive = {
    case req: AppRequest => req match {
      case GetTaskActor(description) =>
        val taskId = UUID.randomUUID().toString
        sender ! TaskActorReply(context.actorOf(Props(new ChildTaskActor(taskId, description, sbts)), name = taskId))
    }
  }

  // this actor corresponds to one protocol.Request, and any
  // protocol.Event that are associated with said request.
  // This is spawned from ChildTaskActor for each request.
  class ChildRequestActor(val requestor: ActorRef, val sbt: ActorRef, val request: protocol.Request) extends Actor {
    sbt ! request

    override def receive = {
      case response: protocol.Response =>
        requestor.forward(response)
        // Response is supposed to arrive at the end,
        // after all Event
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
    val reservation = SbtReservation(id = UUID.randomUUID().toString(), taskName = taskDescription)

    pool ! RequestAnSbt(reservation)

    private def handleRequest(requestor: ActorRef, sbt: ActorRef, request: protocol.Request) = {
      context.actorOf(Props(new ChildRequestActor(requestor = requestor,
        sbt = sbt, request = request)))
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

      // when we die, the reservation should be auto-released by ServerActor
    }

    private def haveSbt(sbt: ActorRef): Receive = {
      case req: protocol.Request => handleRequest(sender, sbt, req)
      case Terminated(ref) => self ! PoisonPill // our sbt died
    }
  }
}
