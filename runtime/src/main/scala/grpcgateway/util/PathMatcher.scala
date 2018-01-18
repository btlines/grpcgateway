package grpcgateway.util

import scala.collection.mutable

private[util] trait PathMatcher {
  def matchString(str: String, from: Int, templateParams: mutable.Map[String, String]) : Int
}

private[util] final class TextMatcher(prefix: String) extends PathMatcher {
  override def matchString(str: String, from: Int, map: mutable.Map[String, String]): Int = {
    val to = from + prefix.length
    if (str.substring(from, to) == prefix) {
      to
    } else {
      throw new IllegalArgumentException(s"Prefix $prefix not found at $from in $str")
    }
  }

  override def toString: String = prefix
}

private[util] final class TemplateMatcher(name: String) extends PathMatcher {
  override def matchString(str: String, from: Int, map: mutable.Map[String, String]): Int = {
    var index = from
    while ((index < str.length) && (str(index) != '/')) {
      index += 1
    }

    map.put(name, str.substring(from, index))

    index
  }

  override def toString: String = s"[$name]"
}

