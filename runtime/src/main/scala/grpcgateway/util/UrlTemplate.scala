package grpcgateway.util

import io.netty.handler.codec.http.QueryStringDecoder

import scala.collection.mutable

/** A means of parsing request URLs with support for "URL parameter templates" configured in a protobuf descriptor */
trait UrlTemplate {
  /**
    * Match incoming URI against this URL template generated from a protobuf RESTful service descriptor
    *
    * Netty's FullHttpRequest.uri() happens to return a path so we currently assume the "protocol/host" prefix
    * is stripped before this method is called.
    *
    * @return URL properties extracted from the URI if it matched this template
    */
  def matchUri(uri: String) : Option[RestfulUrl]
}

/** Fast path for URL patterns with no templates */
private final class PlainUrlTemplate(path: String) extends UrlTemplate {
  override def matchUri(uri: String): Option[RestfulUrl] = {
    //println(s"Matching \'$uri\' to $path")

    val decoder = new QueryStringDecoder(uri)
    if (decoder.path() == path) {
      Some(new PlainRestfulUrl(decoder.parameters()))
    } else {
      None
    }
  }
}

/** Remember a sequence of matchers to apply (in the same order) to an incoming URI. While matching, optimistically
  * collect values at positions corresponding to names slots in the original URL template */
private final class MatchingUrlTemplate(matchers: Seq[PathMatcher]) extends UrlTemplate {
  override def matchUri(uri: String): Option[RestfulUrl] = {
    val decoder = new QueryStringDecoder(uri)
    val path = decoder.path()

    //println(s"Matching \'$path\' with ${matchers.mkString}")

    var pathIndex = 0
    var matcherIndex = 0

    val templateParams = mutable.Map[String, String]()
    while (pathIndex < path.length) {
      val from = pathIndex

      val matcher = matchers(matcherIndex)
      pathIndex = matcher.matchString(path, pathIndex, templateParams)

      //println(s"Matched \'${path.substring(from, pathIndex)}\' with ${matcher.toString} remains [${path.substring(pathIndex)}]")

      matcherIndex += 1
    }

    if (matcherIndex == matchers.size) {
      Some(new MergedRestfulUrl(templateParams, decoder.parameters()))
    } else {
      None
    }
  }
}

object UrlTemplate {
  /** Parse a URL template into a URL matcher. Use a fast path for templates with no named slots. */
  def apply(path: String) : UrlTemplate = {
    if (PathParser.hasTemplates(path)) {
      new MatchingUrlTemplate(PathParser(path))
    } else {
      new PlainUrlTemplate(path)
    }
  }
}
