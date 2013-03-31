import akka.actor.*;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PiTest {

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        system.shutdown();
    }

    @Test
    public void testPi() {
        new JavaTestKit(system) {{
            
            final ActorRef master = system.actorOf(new Props(new UntypedActorFactory() {
                public UntypedActor create() {
                    return new PiJava.Master(4, 10000, 10000, getRef());
                }
            }), "master");
            
            final JavaTestKit probe = new JavaTestKit(system);
                    
            master.tell(new PiJava.Calculate());
            
            final PiJava.PiApproximation piApproximation = expectMsgClass(PiJava.PiApproximation.class);

            new Within(duration("10 seconds")) {
                protected void run() {
                    Assert.assertTrue(Double.toString(piApproximation.getPi()).startsWith("3.1415"));
                }
            };
            
        }};
    }

}