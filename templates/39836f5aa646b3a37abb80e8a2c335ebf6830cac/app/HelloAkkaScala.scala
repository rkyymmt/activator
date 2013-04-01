import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ActorSystem, Props, Actor}

case object SayHello

class HelloActorScala extends Actor {
  def receive = {
    case SayHello => println("hello, world")
  }
}

object HelloAkkaScala extends App {
  val system = ActorSystem("helloakka")
  
  system.scheduler.schedule(Duration.Zero,
    Duration.create(5, SECONDS),
    system.actorOf(Props[HelloActorScala]),
    SayHello)
  
}
