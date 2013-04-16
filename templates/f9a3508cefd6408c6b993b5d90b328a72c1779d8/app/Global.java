import actors.FetchLatest;
import actors.SetupStock;
import actors.StockHolderActor;
import actors.UsersActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import play.Application;
import play.GlobalSettings;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Global extends GlobalSettings {

    @Override
    public void onStart(Application application) {

        ActorRef usersActor = Akka.system().actorOf(new Props(UsersActor.class), "users");
        
        ActorRef stockHolderActor = Akka.system().actorOf(new Props(StockHolderActor.class), "stocks");

        // fetch a new data point once every second
        Akka.system().scheduler().schedule(Duration.Zero(), Duration.create(1, TimeUnit.SECONDS), stockHolderActor, FetchLatest.instance(), Akka.system().dispatcher());

        super.onStart(application);
    }
}
