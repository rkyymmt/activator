package controllers;


import actors.UnwatchStock;
import actors.WatchStock;
import akka.actor.ActorRef;
import org.codehaus.jackson.JsonNode;
import play.Play;
import play.libs.Akka;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;

import actors.Listen;

import java.util.List;

public class Application extends Controller {

    public static Result index() {
        return ok(views.html.index.render());
    }

    public static WebSocket<JsonNode> listen(final String uuid) {
        return new WebSocket<JsonNode>() {
            public void onReady(WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out){
                ActorRef usersActor = Akka.system().actorFor("user/users");
                usersActor.tell(new Listen(uuid, out), usersActor);
                
                // watch the default stocks
                List<String> defaultStocks = Play.application().configuration().getStringList("default.stocks");
                for (String symbol : defaultStocks) {
                    usersActor.tell(new WatchStock(uuid, symbol), usersActor);
                }
            }
        };
    }
    
    public static Result watch(String uuid, String symbol) {
        ActorRef usersActor = Akka.system().actorFor("user/users/" + uuid);
        usersActor.tell(new WatchStock(uuid, symbol), usersActor);
        return ok();
    }

    public static Result unwatch(String uuid, String symbol) {
        ActorRef usersActor = Akka.system().actorFor("user/users/" + uuid);
        usersActor.tell(new UnwatchStock(uuid, symbol), usersActor);
        return ok();
    }

}