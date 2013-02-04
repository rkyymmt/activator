import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import snap.EnhancedURI._
import java.net.URI
import java.net.URISyntaxException

class EnhancedURITest extends Specification {

  "EnhancedURI" should {

    def testCopy(s: String) = {
      val u = new URI(s)
      val c = u.copy()
      u must equalTo(c)
      val c2 = new URI(u.toASCIIString())
      u must equalTo(c2)
      val c3 = new URI(u.toString())
      u must equalTo(c3)
    }

    "copy a URI with all elements correctly" in {
      testCopy("http://user:pass@example.com:32/?a=/foo/bar%25%3D%23&some=something#fragment")
    }

    "copy a URI with only a host" in {
      testCopy("http://example.com")
    }

    "copy a URI with only a host and a path" in {
      testCopy("http://example.com/foo")
    }

    "copy a URI with only a path" in {
      testCopy("/foo/bar")
    }

    "copy a URI with only a path and query" in {
      testCopy("/foo/bar?hello=world")
    }

    "handle encoding &=%" in {
      val u = new URI("http://example.com").addQueryParameter("a", "&=%")
      u.getRawQuery() must equalTo("a=%26%3D%25")
    }

    "replace a first query parameter" in {
      val uri = new URI("http://example.com/")
      val withParam = uri.replaceQueryParameter("a", "b/")
      withParam.getRawQuery() must equalTo("a=b%2F")
    }

    "replace a second query parameter" in {
      val uri = new URI("http://example.com/?c=d%2F")
      val withParam = uri.replaceQueryParameter("a", "b/")
      withParam.getRawQuery() must equalTo("a=b%2F&c=d%2F")
    }

    "replace a third query parameter" in {
      val uri = new URI("http://example.com/?x=y&c=d%2F")
      val withParam = uri.replaceQueryParameter("a", "b/")
      withParam.getRawQuery() must equalTo("a=b%2F&c=d%2F&x=y")
    }

    "replace a duplicate query parameter" in {
      val uri = new URI("http://example.com/?x=y%2F&c=d")
      val withParam = uri.replaceQueryParameter("c", "e/")
      withParam.getRawQuery() must equalTo("c=e%2F&x=y%2F")
    }

    "add a first query parameter" in {
      val uri = new URI("http://example.com/")
      val withParam = uri.addQueryParameter("a", "b/")
      withParam.getRawQuery() must equalTo("a=b%2F")
    }

    "add a second query parameter" in {
      val uri = new URI("http://example.com/?c=d%2F")
      val withParam = uri.addQueryParameter("a", "b/")
      withParam.getRawQuery() must equalTo("a=b%2F&c=d%2F")
    }

    "add a third query parameter" in {
      val uri = new URI("http://example.com/?x=y&c=d%2F")
      val withParam = uri.addQueryParameter("a", "b/")
      withParam.getRawQuery() must equalTo("a=b%2F&c=d%2F&x=y")
    }

    "add a duplicate query parameter" in {
      val uri = new URI("http://example.com/?x=y%2F&c=d")
      val withParam = uri.addQueryParameter("c", "e/")
      withParam.getRawQuery() must equalTo("c=d&c=e%2F&x=y%2F")
    }

    "handle empty query parameters" in {
      val uri = new URI("http://example.com/?c=")
      val withParam = uri.replaceQueryParameter("a", "")
      withParam.getRawQuery() must equalTo("a=&c=")
    }

    "handle encoding query parameters" in {
      val uri = new URI("http://example.com/?c=+")
      val withParam = uri.replaceQueryParameter("a", " ")
      withParam.getRawQuery() must equalTo("a=+&c=+")
    }

    "replace with encoded query string" in {
      val uri = new URI("http://example.com/?a=b&a=c")
      val changed = uri.replaceQueryParameters("a=x&y=z&y=1&a=z")
      changed.getRawQuery() must equalTo("a=x&a=z&y=z&y=1")
    }

    "parsing query keeps the order with same key" in {
      val uri = new URI("http://example.com/?x=y")
      val changed = uri.replaceQueryParameters("a=1&a=2&a=3&a=4&a=5&a=6&a=7")
      changed.getRawQuery() must equalTo("a=1&a=2&a=3&a=4&a=5&a=6&a=7&x=y")
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
