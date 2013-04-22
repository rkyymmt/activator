import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import akka.dispatch.*;
import static akka.pattern.Patterns.ask;

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
            if (message instanceof WhoToGreet)
                greeting = "hello, " + ((WhoToGreet)message).who;
            else if (message instanceof Greet)
                getSender().tell(new Greeting(greeting), getSelf());
        }
    }

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("helloakka");
        final ActorRef greeter = system.actorOf(new Props(Greeter.class), "greeter");

        final ExecutionContext ec = system.dispatcher();
        final Timeout timeout = new Timeout(Duration.create(5, "seconds"));

        // tell the greeter to change its greeting
        greeter.tell(new WhoToGreet("akka"));

        // define a println 'foreach' function
        final Foreach<Object> println = new Foreach<Object>() {
            public void each(Object o) {
                System.out.println("Greeting: " + ((Greeting)o).message);
            }
        };

        // ask the greeter for its current greeting
        final Future<Object> f1 = ask(greeter, new Greet(), timeout);
        f1.foreach(println, ec);

        // change the greeting and ask for it again
        greeter.tell(new WhoToGreet("typesafe"));
        final Future<Object> f2 = ask(greeter, new Greet(), timeout);
        f2.foreach(println, ec);

        system.shutdown();
    }
}
