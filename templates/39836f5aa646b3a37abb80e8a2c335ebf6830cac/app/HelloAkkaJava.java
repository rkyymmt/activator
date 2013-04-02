import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import scala.concurrent.duration.Duration;
import static java.util.concurrent.TimeUnit.*;

public class HelloAkkaJava {

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("helloakka");
        system.scheduler().schedule(Duration.Zero(),
            Duration.create(5, SECONDS),
            system.actorOf(new Props(HelloActorJava.class)),
            "sayhello",
            system.dispatcher()
        );
    }

    public static class HelloActorJava extends UntypedActor {

        public void onReceive(Object message) {
            if (message.equals("sayhello"))
                System.out.println("hello, world");
        }
    }

}
