package scala.swing

import java.awt.CardLayout
import javax.swing.JPanel

class CardPanel extends Panel with SequentialContainer.Wrapper {
  
  private lazy val cardLayout = new CardLayout
  
  override lazy val peer = {
    val p = new JPanel with SuperMixin
    p.setLayout(cardLayout)
    p
  }
  
  def next {
    cardLayout.next(peer)
  }
  
  def first {
    cardLayout.first(peer)
  }
  
  def last {
    cardLayout.last(peer)
  }
  
  def show(name: String) {
    cardLayout.show(peer, name)
  }
  
}
