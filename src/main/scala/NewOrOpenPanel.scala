import swing._
import swing.Swing._

object NewOrOpenPanel {

  lazy val ui = new BoxPanel(Orientation.Vertical) {
    override val name = "NewOrOpenPanel"

    val newButton = new BoxPanel(Orientation.NoOrientation) {
      border = EmptyBorder(5, 5, 5, 5)
      contents += new Button(Action("New App") {
        Launcher.showNewAppPanel
      })
    }

    val openButton = new BoxPanel(Orientation.NoOrientation) {
      border = EmptyBorder(5, 5, 5, 5)
      contents += new Button("Open...")
    }


    val recentItems = new BoxPanel(Orientation.NoOrientation) {
      border = CompoundBorder(EmptyBorder, TitledBorder(EtchedBorder, "Recent Apps"))
      val items = List("foo (/home/jamesw/foo)", "bar (/home/jamesw/project/bar)", "computer-database (/home/jamesw/projects/computer-database)", "HelloScala (/home/jamesw/projects/helloscala)")
      val view = new ListView(items) // todo: these should be links with underlines
      contents += view
    }

    contents ++= Seq(newButton, openButton, recentItems)
  }

}