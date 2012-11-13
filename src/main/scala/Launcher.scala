import java.awt.Dimension
import javax.swing.border.EmptyBorder
import swing.BorderPanel.Position
import swing._

object Launcher extends SimpleSwingApplication {
  
  lazy val cardPanel = new CardPanel {
    contents ++= Seq(NewOrOpenPanel.ui, NewAppPanel.ui, AppPanel.ui)
  }
  
  def top = new MainFrame {
    title = "Typesafe Launcher"
    contents = cardPanel
    size = new Dimension(800, 600)
  }
  
  def showNewOrOpenPanel {
    cardPanel.first
    //cardPanel.show(NewOrOpenPanel.ui.name)
  }

  def showNewAppPanel {
    cardPanel.next
    //cardPanel.show(NewAppPanel.ui.name)
  }

  def showAppPanel {
    cardPanel.last
    //cardPanel.show(AppPanel.ui.name)
  }
  
  var app: Option[App] = None
  
}