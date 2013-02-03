/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package models

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

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
  def copy(scheme: String = uri.getScheme(), userInfo: String = uri.getUserInfo(),
           host: String = uri.getHost(), port: Int = uri.getPort(), path: String = uri.getPath(),
           query: String = uri.getQuery(), fragment: String = uri.getFragment()): URI = {
    new URI(scheme, userInfo, host, port, path, query, fragment);
  }

  /** Replace (or add if not present) one query parameter */
  def replaceQueryParameter(key: String, value: String): URI = {
    replaceQueryParameters(Map(key -> List(value)))
  }

  /** Replace (or add if not present) a set of query parameters */
  def replaceQueryParameters(params: Map[String, Seq[String]]): URI = {
    val existing = uri.getQuery()
    if (existing == null)
      copy(query = encodeQuery(params))
    else
      copy(query = encodeQuery(parseQuery(existing) ++ params))
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
    val existing = uri.getQuery()
    if (existing == null)
      copy(query = encodeQuery(params))
    else
      copy(query = encodeQuery(mergeQuery(parseQuery(existing), params)))
  }

  /** Append to the URI's path, avoiding duplicate "/" */
  def appendToPath(morePath: String): URI = {
    val startsWithSlash = if (morePath.startsWith("/") || morePath.length == 0)
      morePath
    else
      "/" + morePath
    val existing = uri.getPath()
    if (existing == null) {
      copy(path = startsWithSlash)
    } else if (existing.endsWith("/")) {
      copy(path = existing + startsWithSlash.substring(1))
    } else {
      copy(path = existing + startsWithSlash)
    }
  }

  private def parseOnePair(keyEqualsValue: String): Map[String, String] = {
    if (keyEqualsValue.length() == 0) {
      Map.empty
    } else {
      val e = keyEqualsValue.indexOf('=')
      if (e < 0) {
        Map(keyEqualsValue -> "")
      } else {
        Map(keyEqualsValue.substring(0, e) -> URLDecoder.decode(keyEqualsValue.substring(e + 1), "UTF-8"))
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
    query.toSeq.sortBy(_._1).foldLeft("")({ (sofar, kv) ⇒
      val pairs = for { v ← kv._2 }
        yield kv._1 + "=" + URLEncoder.encode(v, "UTF-8")
      val encoded = pairs.mkString("&")
      if (sofar.length() == 0)
        encoded
      else
        sofar + "&" + encoded
    })
  }

  private def mergeQuery(left: Map[String, Seq[String]], right: Map[String, Seq[String]]): Map[String, Seq[String]] = {
    right.foldLeft(left)({ (sofar, kv) ⇒
      val dup = sofar.get(kv._1)
      sofar + (kv._1 -> dup.map(_ ++ kv._2).getOrElse(kv._2))
    })
  }
}
