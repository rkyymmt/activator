package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.ws.{Response, WS}
import utils.Global
import scala.concurrent.Future
import play.api.libs.json.{JsString, Json, JsValue}

object StockSentiment extends Controller {

  def getTextSentiment(text: String): Future[Response] = WS.url(Global.sentimentUrl) post Map("text" -> Seq(text))
  
  def getAverageSentiment(responses: Seq[Response], label: String) = responses.map { response =>
    (response.json \\ label).head.as[Double]
  }.sum / responses.length
  
  def get(symbol: String) = Action {
    Async {
      val tweetsFuture = WS.url(Global.tweetUrl.format(symbol)).get

      for {
        tweets <- tweetsFuture
        sentiments <- Future.sequence((tweets.json \\ "text").map(_.as[String]) map getTextSentiment)
      }
      yield {
        val neg = getAverageSentiment(sentiments, "neg")
        val neutral = getAverageSentiment(sentiments, "neutral")
        val pos = getAverageSentiment(sentiments, "pos")
        
        val response = Json.obj(
          "probability" -> Json.obj(
            "neg" -> neg,
            "neutral" -> neutral,
            "pos" -> pos 
          )
        )
        
        if (neutral > 0.5)
          Ok(response + ("label" -> JsString("neutral")))
        else if (neg > pos)
          Ok(response + ("label" -> JsString("neg")))
        else
          Ok(response + ("label" -> JsString("pos")))
      }
    }
  }

}