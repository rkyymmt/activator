import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Inbox;
import scala.concurrent.duration.Duration;

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
                // Send the current greeting back to the sender
                getSender().tell(new Greeting(greeting), getSelf());
        }
    }

    public static void main(String[] args) {
        // Create the 'helloakka' actor system
        final ActorSystem system = ActorSystem.create("helloakka");

        // Create the 'greeter' actor
        final ActorRef greeter = system.actorOf(new Props(Greeter.class), "greeter");

        // Create the mailbox
        final Inbox inbox = Inbox.create(system);

        // Tell the 'greeter' to change its 'greeting' message
        greeter.tell(new WhoToGreet("akka"));

        // Ask the 'greeter for the latest 'greeting'
        // Reply should go to the mailbox
        inbox.send(greeter, new Greet());

        // Wait 5 seconds for the reply with the 'greeting' message
        Greeting greeting1 = (Greeting)inbox.receive(Duration.create(5, "seconds"));
        System.out.println("Greeting: " + greeting1.message);

        // Change the greeting and ask for it again
        greeter.tell(new WhoToGreet("typesafe"));
        inbox.send(greeter, new Greet());
        Greeting greeting2 = (Greeting)inbox.receive(Duration.create(5, "seconds"));
        System.out.println("Greeting: " + greeting2.message);

        system.shutdown();
    }
}
