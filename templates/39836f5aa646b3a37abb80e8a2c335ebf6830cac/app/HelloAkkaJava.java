import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import akka.japi.Procedure;
import akka.dispatch.*;
import akka.pattern.Patterns;

import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;

import static java.util.concurrent.TimeUnit.*;

public class HelloAkkaJava {
    public static class Greet {}
    public static class WhoToGreet {
        public final String who;
        public WhoToGreet(String who) {
            this.who = who;
        }
    }
    public static class Greeting {
        public final String message;
        public Greeting(String message) {
            this.message = message;
        }
    }

    public static class Greeter extends UntypedActor {
        String greeting = "";

        public void onReceive(Object message) {
            if (message instanceof WhoToGreet) {
                greeting = "hello, " + ((WhoToGreet)message).who;
                System.out.println("Changed greeting to " + greeting);
            } else if (message instanceof Greet) {
                getSender().tell(new Greeting(greeting));
            }
        }
    }

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("helloakka");
        final ActorRef greeter = system.actorOf(new Props(Greeter.class), "greeter");

        final ExecutionContext ec = system.dispatcher();
        final Timeout timeout = new Timeout(Duration.create(5, "seconds"));

        // tell the greeter to change its greeting
        greeter.tell(new WhoToGreet("akka"));

        // ask the greeter for its current greeting
        final Future<Object> f = Patterns.ask(greeter, new Greet(), timeout);
        f.onSuccess(new OnSuccess<Object>() {
          public void onSuccess(Object o) {
            System.out.println(o);
          }
        }, ec);

        system.shutdown();
    }
}
