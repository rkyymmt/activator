import org.scalatest._
import org.scalatest.matchers.ShouldMatchers

class HelloSpec extends FlatSpec with ShouldMatchers {
  "Hello" should "exist" in {
    val hello = Hello
    hello should not be null
  }
}
