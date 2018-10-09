package grpcgateway.util

import scala.collection.mutable

private[util] trait PathMatcher {
  /**
    * Assume a sequence of matchers was created by sequentially scanning a URL pattern such as "/get/{template}".
    * One by one apply all matchers in the original order
    * @param str URL path string
    * @param from position in the path to start matching from
    * @param templateParams (name,value) pairs of URL parameters extracted from named slots
    * @return the position in string the next matcher should continue from
    */
  def matchString(str: String, from: Int, templateParams: mutable.Map[String, String]) : Int
}

private[util] final class TextMatcher(prefix: String) extends PathMatcher {
  override def matchString(str: String, from: Int, templateParams: mutable.Map[String, String]): Int = {
    val to = from + prefix.length

    if ((to <= str.length) && (str.substring(from, to) == prefix)) {
      to
    } else {
      PathMatcher.NO_MATCH
    }
  }

  override def toString: String = prefix
}

private[util] final class TemplateMatcher(name: String) extends PathMatcher {
  private val PATH_DELIMITER = '/'

  override def matchString(str: String, from: Int, templateParams: mutable.Map[String, String]): Int = {
    var index = from
    while ((index < str.length) && (str(index) != PATH_DELIMITER)) {
      index += 1
    }

    templateParams.put(name, str.substring(from, index))

    index
  }

  override def toString: String = s"[$name]"
}

object PathMatcher {
  /** The "string position" value to return if a string does not match this matcher */
  val NO_MATCH: Int = -1
}