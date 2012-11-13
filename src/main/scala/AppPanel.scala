import scala.swing._
import scala.swing.Swing._
import scala.swing.GridBagPanel.{Anchor, Fill}

object AppPanel {
  
  lazy val ui = new BorderPanel {
    override val name = "AppPanel"

    val appName = new Label
    
    def refreshAppName {

      Launcher.app.map { app =>
        appName.text = app.name + " (" + app.location + ")"
      }

    }
    
    val top = new BoxPanel(Orientation.Vertical) {
    
      val infoLine = new BoxPanel(Orientation.Horizontal) {
  
        val closeButton = new Button(Action("X") {
          Launcher.showNewOrOpenPanel
        })
        
        contents ++= Seq(appName, HGlue, closeButton)
      }

      val runButtons = new FlowPanel {

        val runAppButton = new ToggleButton("Run App") // runs continuously when down

        // todo: doesn't make sense in non-web apps
        val running = new FlowPanel {
          val label = new Label("Running at:")
          val link = new Label("http://localhost:9000") { // make it a link

          }

          contents ++= Seq(label, link)
        }
        None
        val runTestsButton = new ToggleButton("Run Tests") // runs continuously when down

        contents ++= Seq(runAppButton, runTestsButton)
      }

      val editButtons = new FlowPanel {
        val label = new Label("Edit in: ")
        val browserButton = new Button(Action("Browser") {

        })
        val eclipseButton = new Button(Action("Eclipse") {

        })
        val ideaButton = new Button(Action("IntelliJ") {

        })

        contents ++= Seq(label, browserButton, eclipseButton, ideaButton)
      }
      
      val modules = new FlowPanel {
        border = CompoundBorder(EmptyBorder, TitledBorder(EtchedBorder, "Modules"))
        contents += new Button("Deploy on Heroku")
        contents += new Button("Sync with GitHub")
        contents += new Button("Setup CI on CloudBees")
        contents += new Button("Analyize performance with NewRelic")
      }

      val moduleBrowser = new Label("Add Modules...")

      val deployButton = new Button(Action("Deploy...") {

      })
      
      contents ++= Seq(infoLine, runButtons, editButtons, moduleBrowser, deployButton)
    }
    
    layout(top) = BorderPanel.Position.North

    val logs = new BoxPanel(Orientation.NoOrientation) {
      border = CompoundBorder(EmptyBorder, TitledBorder(EtchedBorder, "Logs"))
      val output = new ScrollPane(new TextArea {
        editable = false
      })

      contents += output
    }
    
    layout(logs) = BorderPanel.Position.Center
  }

}