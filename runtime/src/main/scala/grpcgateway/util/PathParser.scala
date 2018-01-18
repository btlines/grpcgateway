package grpcgateway.util

import scala.collection.mutable.ArrayBuffer

private[util] final class PathParser(path: String) {
  import PathParser.{LCURLY, RCURLY}

  private val matchers = ArrayBuffer[PathMatcher]()
  private var index = 0

  private def matchChar(ch: Char): Unit = {
    if (path(index) == ch) {
      index += 1
    } else {
      throw new IllegalArgumentException(s"Unexpected character ${path(index)} at $index in $path")
    }
  }

  private def matchTemplate(): TemplateMatcher = {
    matchChar(LCURLY)

    val from = index
    while ((index < path.length) && (path(index) != RCURLY)) {
      index += 1
    }
    val name = path.substring(from, index)

    matchChar(RCURLY)

    new TemplateMatcher(name)
  }

  private def matchPrefix(): TextMatcher = {
    val from = index
    while ((index < path.length) && (path(index) != LCURLY)) {
      index += 1
    }

    new TextMatcher(path.substring(from, index))
  }

  def parse() : Seq[PathMatcher] = {
    while (index < path.length) {
      path(index) match {
        case LCURLY =>
          val matcher = matchTemplate()
          matchers += matcher

        case _ =>
          val matcher = matchPrefix()
          matchers += matcher
      }
    }

    matchers
  }

  override def toString: String = matchers.mkString
}

private[util] object PathParser {
  private val LCURLY = '{'
  private val RCURLY = '}'

  def hasTemplates(path: String) : Boolean = path.contains(PathParser.LCURLY)
}
