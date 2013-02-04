/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package snap

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import language.implicitConversions
import java.net.URISyntaxException

object EnhancedURI {
  implicit def uri2enhanced(uri: URI) = new EnhancedURI(uri)
}

/**
 * Enhance URI class to support fluent modifications:
 *  - copy() changing any field
 *  - replaceQueryParameter / addQueryParameter
 *  - appendToPath
 *
 * uri.copy(port=9001).appendToPath("bar").replaceQueryParameter("foo", "bar")
 *
 * This does not support URI schemes with an "authority" that is
 * not in the userInfo@hostname:port format.
 * Writing this seems basically crazy but I didn't see anything
 * in the jars we depend on that could be used.
 *
 * The query utilities do allow multiple values for one key.
 */
class EnhancedURI(val uri: URI) {
  /** Copy a URI case-class-style, changing only some fields by keyword */
  def copy(scheme: String = uri.getScheme(), userInfo: String = uri.getRawUserInfo(),
    host: String = uri.getHost(), port: Int = uri.getPort(), path: String = uri.getRawPath(),
    query: String = uri.getRawQuery(), fragment: String = uri.getRawFragment()): URI = {
    // We can't use the multi-parameter URI constructors because they are busted;
    // the query string is expected to have nothing hex-escaped...
    // but then you can't have & or = in the parameter values.
    // (It's possible the path, etc. handling is similarly busted, not sure.)
    // So we instead of have to re-implement URI.toString and then re-parse it,
    // because that's the only way I know to get java.net.URI to load a correctly-escaped
    // query string. Software... sigh.
    def ipv6ify(h: String) = if (h.contains(':') && !h.startsWith("[")) "[" + h + "]" else h
    def opt(s: String)(f: String => String): String = Option(s).map(f).getOrElse("")
    val s = opt(scheme)(_ + ":") +
      opt(host) { host =>
        "//" +
          opt(userInfo)(_ + "@") +
          ipv6ify(host) +
          (if (port != -1) ":" + port else "")
      } +
      opt(path)(identity) +
      opt(query)("?" + _) +
      opt(fragment)("#" + _)
    new URI(s)
  }

  /** Replace (or add if not present) one query parameter */
  def replaceQueryParameter(key: String, value: String): URI = {
    replaceQueryParameters(Map(key -> List(value)))
  }

  /** Replace (or add if not present) a set of query parameters */
  def replaceQueryParameters(params: Map[String, Seq[String]]): URI = {
    val existing = uri.getRawQuery()
    copy(query = {
      if (existing eq null)
        encodeQuery(params)
      else
        encodeQuery(parseQuery(existing) ++ params)
    })
  }

  /** Replace (or add if not present) the params in the encoded query string */
  def replaceQueryParameters(encodedParams: String): URI = {
    replaceQueryParameters(parseQuery(encodedParams))
  }

  /** Add one query parameter creating a duplicate if already present */
  def addQueryParameter(key: String, value: String): URI = {
    addQueryParameters(Map(key -> List(value)))
  }

  /** Add a set of query parameters creating duplicates if already present */
  def addQueryParameters(params: Map[String, Seq[String]]): URI = {
    val existing = uri.getRawQuery()
    copy(query = {
      if (existing eq null)
        encodeQuery(params)
      else
        encodeQuery(mergeQuery(parseQuery(existing), params))
    })
  }

  /** Append to the URI's path, avoiding duplicate "/" */
  def appendToPath(morePath: String): URI = {
    val startsWithSlash = if (morePath.startsWith("/") || morePath.length == 0)
      morePath
    else
      "/" + morePath
    val existing = uri.getPath()
    copy(path = {
      if (existing eq null)
        startsWithSlash
      else if (existing.endsWith("/"))
        existing + startsWithSlash.substring(1)
      else
        existing + startsWithSlash
    })
  }

  private def decodeValue(encoded: String) = {
    URLDecoder.decode(encoded, "UTF-8")
  }

  private def encodeValue(decoded: String) = {
    URLEncoder.encode(decoded, "UTF-8")
  }

  private def parseOnePair(keyEqualsValue: String): Map[String, String] = {
    if (keyEqualsValue.isEmpty() == 0) {
      Map.empty
    } else {
      val e = keyEqualsValue.indexOf('=')
      if (e < 0) {
        Map(keyEqualsValue -> "")
      } else {
        Map(keyEqualsValue.substring(0, e) -> decodeValue(keyEqualsValue.substring(e + 1)))
      }
    }
  }

  private def splitQuery(query: String): (Map[String, String], String) = {
    val i = query.indexOf('&')
    if (i < 0) {
      (parseOnePair(query), "")
    } else if (i == 0) {
      splitQuery(query.substring(1))
    } else {
      (parseOnePair(query.substring(0, i)), query.substring(i + 1))
    }
  }

  private def parseQuery(query: String): Map[String, Seq[String]] = {
    if (query.length() == 0) {
      Map.empty
    } else {
      val (first, remaining) = splitQuery(query)
      val others = parseQuery(remaining)
      mergeQuery(first.mapValues(Seq(_)), others)
    }
  }

  private def encodeQuery(query: Map[String, Seq[String]]): String = {
    // we sort the query string because deterministic order
    // makes the test suite a lot easier
    query.toSeq.sortBy(_._1).foldLeft("") {
      case (sofar, (key, value)) ⇒
        val pairs = for { v ← value }
          yield key + "=" + encodeValue(v)
        val encoded = pairs.mkString("&")
        if (sofar.length() == 0)
          encoded
        else
          sofar + "&" + encoded
    }
  }

  private def mergeQuery(left: Map[String, Seq[String]], right: Map[String, Seq[String]]): Map[String, Seq[String]] = {
    right.foldLeft(left) {
      case (sofar, (key, value)) ⇒
        val dup = sofar.get(key)
        sofar + (key -> dup.map(_ ++ value).getOrElse(value))
    }
  }
}
