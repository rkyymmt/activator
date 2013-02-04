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

  // this actor's lifetime corresponds to one sequence of interactions with
  // an sbt instance obtained from the sbt pool.
  // It gets the pool from the app; reserves an sbt in the pool; and
  // forwards any messages you like to that pool.
  class ChildTaskActor(val taskId: String, val taskDescription: String, val pool: ActorRef) extends Actor {
    val reservation = SbtReservation(id = UUID.randomUUID().toString(), taskName = taskDescription)

    pool ! RequestAnSbt(reservation)

    override def receive = gettingReservation(Nil)

    private def gettingReservation(requestQueue: List[(ActorRef, protocol.Request)]): Receive = {
      case req: protocol.Request => context.become(gettingReservation((sender, req) :: requestQueue))
      case SbtGranted(filled) =>
        val sbt = filled.sbt.getOrElse(throw new RuntimeException("we were granted a reservation with no sbt"))
        // send the queue
        requestQueue.reverse.foreach(tuple => sbt.tell(tuple._2, tuple._1))
        // monitor sbt death
        context.watch(sbt)
        // now enter have-sbt mode
        context.become(haveSbt(sbt))

      // when we die, the reservation should be auto-released
    }

    private def haveSbt(sbt: ActorRef): Receive = {
      case req: protocol.Request => sbt.forward(req)
      case Terminated(ref) => self ! PoisonPill // our sbt died
    }
  }
}
