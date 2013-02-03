import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import models.EnhancedURI._
import java.net.URI

class EnhancedURITest extends Specification {

  "EnhancedURI" should {

    "replace a first query parameter" in {
      val uri = new URI("http://example.com/")
      val withParam = uri.replaceQueryParameter("a", "b")
      withParam.getQuery() must equalTo("a=b")
    }

    "replace a second query parameter" in {
      val uri = new URI("http://example.com/?c=d")
      val withParam = uri.replaceQueryParameter("a", "b")
      withParam.getQuery() must equalTo("a=b&c=d")
    }

    "replace a third query parameter" in {
      val uri = new URI("http://example.com/?x=y&c=d")
      val withParam = uri.replaceQueryParameter("a", "b")
      withParam.getQuery() must equalTo("a=b&c=d&x=y")
    }

    "replace a duplicate query parameter" in {
      val uri = new URI("http://example.com/?x=y&c=d")
      val withParam = uri.replaceQueryParameter("c", "e")
      withParam.getQuery() must equalTo("c=e&x=y")
    }

    "add a first query parameter" in {
      val uri = new URI("http://example.com/")
      val withParam = uri.addQueryParameter("a", "b")
      withParam.getQuery() must equalTo("a=b")
    }

    "add a second query parameter" in {
      val uri = new URI("http://example.com/?c=d")
      val withParam = uri.addQueryParameter("a", "b")
      withParam.getQuery() must equalTo("a=b&c=d")
    }

    "add a third query parameter" in {
      val uri = new URI("http://example.com/?x=y&c=d")
      val withParam = uri.addQueryParameter("a", "b")
      withParam.getQuery() must equalTo("a=b&c=d&x=y")
    }

    "add a duplicate query parameter" in {
      val uri = new URI("http://example.com/?x=y&c=d")
      val withParam = uri.addQueryParameter("c", "e")
      withParam.getQuery() must equalTo("c=d&c=e&x=y")
    }

    "handle empty query parameters" in {
      val uri = new URI("http://example.com/?c=")
      val withParam = uri.replaceQueryParameter("a", "")
      withParam.getQuery() must equalTo("a=&c=")
    }

    "handle encoding query parameters" in {
      val uri = new URI("http://example.com/?c=+")
      val withParam = uri.replaceQueryParameter("a", " ")
      withParam.getQuery() must equalTo("a=+&c=+")
    }

    "replace with encoded query string" in {
      val uri = new URI("http://example.com/?a=b&a=c")
      val changed = uri.replaceQueryParameters("a=x&y=z&y=1&a=z")
      changed.getQuery() must equalTo("a=x&a=z&y=z&y=1")
    }

    "parsing query keeps the order with same key" in {
      val uri = new URI("http://example.com/?x=y")
      val changed = uri.replaceQueryParameters("a=1&a=2&a=3&a=4&a=5&a=6&a=7")
      changed.getQuery() must equalTo("a=1&a=2&a=3&a=4&a=5&a=6&a=7&x=y")
    }

    "add a first path" in {
      val uri = new URI("http://example.com")
      val withPath = uri.appendToPath("foo")
      withPath.getPath must equalTo("/foo")
    }

    "add a first path with trailing slash" in {
      val uri = new URI("http://example.com/")
      val withPath = uri.appendToPath("foo")
      withPath.getPath must equalTo("/foo")
    }

    "add a second path with no trailing slash" in {
      val uri = new URI("http://example.com/bar")
      val withPath = uri.appendToPath("foo")
      withPath.getPath must equalTo("/bar/foo")
    }

    "add a second path with trailing slash" in {
      val uri = new URI("http://example.com/bar/")
      val withPath = uri.appendToPath("foo")
      withPath.getPath must equalTo("/bar/foo")
    }

    "add a second path with no trailing slash and leading slash" in {
      val uri = new URI("http://example.com/bar")
      val withPath = uri.appendToPath("/foo")
      withPath.getPath must equalTo("/bar/foo")
    }

    "add a second path with trailing slash and leading slash" in {
      val uri = new URI("http://example.com/bar/")
      val withPath = uri.appendToPath("/foo")
      withPath.getPath must equalTo("/bar/foo")
    }
  }
}
