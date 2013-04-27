package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;

public class UsersActor extends UntypedActor {

    public void onReceive(Object message) {

        if (message instanceof StockUpdate) {
            for (ActorRef child : getContext().getChildren()) {
                child.tell(message, getSelf());
            }
        }
        else if (message instanceof Listen) {
            final Listen listen = (Listen)message;
            getSender().tell(getContext().actorOf(Props.apply(new Creator<UserActor>() {
                public UserActor create() {
                    return new UserActor(listen.uuid(), listen.out());
                }
            }), listen.uuid()), getSelf());
        }
        else if (message instanceof WatchStock) {
            final WatchStock watchStock = (WatchStock)message;
            getContext().getChild(watchStock.uuid()).tell(watchStock, getSelf());
        }
        else if (message instanceof UnwatchStock) {
            final UnwatchStock unwatchStock = (UnwatchStock)message;
            getContext().getChild(unwatchStock.uuid()).tell(unwatchStock, getSelf());
        }
    }
}
