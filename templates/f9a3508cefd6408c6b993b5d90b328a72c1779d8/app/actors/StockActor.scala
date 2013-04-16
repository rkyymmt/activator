package actors

import akka.actor.{Props, Actor}
import utils.FakeStockQuote
import java.util.Random
import scala.collection.immutable.Queue

class StockActor(symbol: String) extends Actor {
  var stockHistory = {
    lazy val initialPrices: Stream[java.lang.Double] = (new Random().nextDouble * 800) #:: initialPrices.map(previous => FakeStockQuote.newPrice(previous))
    initialPrices.take(50).to[Queue]
  }
  
  def receive = {
    case FetchLatest => {
      val newPrice = FakeStockQuote.newPrice(stockHistory.last.doubleValue())
      stockHistory = stockHistory.drop(1) :+ newPrice
      context.system.actorFor("user/users") ! StockUpdate(symbol, newPrice)
    }
    case FetchHistory => {
      sender ! stockHistory
    }
  }
}

class StockHolderActor extends Actor {
  def receive = {
    case SetupStock(symbol) => {
      val stockActor = context.actorFor(symbol)
      if (stockActor.isTerminated) {
        context.actorOf(Props(new StockActor(symbol)), symbol)
      }
    }
    case FetchLatest => {
      for(child <- context.children) child ! FetchLatest
    }
  }
}

case class FetchLatest()
  
case object FetchLatest {
  def instance = this
}

case class SetupStock(symbol: String)

case class StockUpdate(symbol: String, price: Number)

case class FetchHistory()