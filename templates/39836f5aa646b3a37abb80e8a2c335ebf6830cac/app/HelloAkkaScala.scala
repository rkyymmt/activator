import akka.actor.{ActorSystem, Props, Actor}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._

case object Greet
case class WhoToGreet(who: String)
case class Greeting(message: String)

class Greeter extends Actor {
  var greeting = ""

  def receive = {
    case WhoToGreet(who) =>
      greeting = s"hello, $who"
      println(s"Changed greeting to $greeting")

    case Greet =>
      sender ! Greeting(greeting)
  }
}

object HelloAkkaScala extends App {

  val system = ActorSystem("helloakka")

  import system.dispatcher
  implicit val timeout = Timeout(5 seconds)

  val greeter = system.actorOf(Props[Greeter], "greeter")

  // tell the greeter to change its greeting
  greeter tell WhoToGreet("akka")

  // ask the greeter for its current greeting
  for (Greeting(message) <- greeter ask Greet) println(s"Greeting: $message")

  greeter ! WhoToGreet("typesafe")
  for (Greeting(message) <- greeter ask Greet) println(s"Greeting: $message")

  system.shutdown()
}
