import collection.immutable.StringLike
import java.util.Date
import swing._
import event.ValueChanged

object NewAppPanel {

  lazy val ui = new BorderPanel {
    override val name = "NewAppPanel"
    
    val appNameTextField = new TextField(32)
    
    val appLocationTextField = new TextField(32) {
      text = System.getProperty("user.home")
    }

    val appNameAndLocation = new BoxPanel(Orientation.Vertical) {
      val appName = new FlowPanel {
        contents += new Label("App Name")
        listenTo(appNameTextField)
        reactions += {
          case ValueChanged(fieldComp) => {
            appLocationTextField.text = System.getProperty("user.home") + "/" + appNameTextField.text
            createButton.enabled = (appNameTextField.text.length > 0)
          }
        }
        contents += appNameTextField
      }
  
      val appLocation = new FlowPanel {
        contents += new Label("App Location")
        contents += appLocationTextField
  
        val browse = new Button(Action("Browse...") {
          new FileChooser().showOpenDialog(this)
        })
  
        contents += browse
      }
      
      contents ++= Seq(appName, appLocation)
    }
    
    layout(appNameAndLocation) = BorderPanel.Position.North

    val model: Array[Array[Any]] = Array(Array("Basic Typesafe Stack 2.1", new Date(), "http://typesafe.com/stack", "typesafe,typesafe-stack,scala,akka,play,slick,rdbms", "The Typesafe Stack provides everything you need to build a modern & highly scalable web application."),
        Array("Play Framework 2.1 with Java", new Date(), "http://playframework.org/java", "play,java", "A simple Play application with Java controllers"),
        Array("Clustered Akka 2.1 with Scala", new Date(), "http://akka.io/clustering", "akka,scala,cluster", "A clustered Akka application with Scala"),
        Array("Big Data on the Typesafe Stack with Cassandra", new Date(), "http://typesafe.com/stack/big-data", "typesafe,typesafe-stack,big-data,cassandra", "A Typesafe Stack application that is Big Data ready with Cassandra"),
        Array("Play 2.1 Reactive MongoDB with Coast-to-Coast JSON", new Date(), "http://zenexity.com/reactivemongo", "play,reactive,mongo,json", "A Play 2.1 App with Reactive MongoDB and JSON everywhere from the client to the datastore."),
        Array("Simple Scala App", new Date(), "http://scala-lang.org", "scala", "Your first Scala app"),
        Array("Play 2.1 with WebJars", new Date(), "http://webjars.org", "play,webjars", "A simple Play 2.1 App that uses WebJars for the client-side dependencies"))
      
    val table = new Table(model, Seq("Name", "Published", "Documentation", "Tags", "Description"))

    layout(new ScrollPane(table)) = BorderPanel.Position.Center
    
    val createButton = new Button {
      enabled = false
      action = Action("Create!") {
        Launcher.app = Some(App(appNameTextField.text, appLocationTextField.text))
        AppPanel.ui.refreshAppName
        Launcher.showAppPanel
      }
    }
    
    layout(createButton) = BorderPanel.Position.South
  }

}