package grpcgateway.util

import scala.collection.mutable.ArrayBuffer

private final class PathParser(path: String) {
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

  /** Assume the index points to a named slot. Remember the extracted slot name. */
  private def matchTemplate(): TemplateMatcher = {
    matchChar(LCURLY)

    val from = index
    while ((index < path.length) && (path(index) != RCURLY)) {
      if (path(index) == LCURLY) {
        throw new IllegalArgumentException(s"Detected curly braces mismatch at ${from-1} and $index in $path")
      }
      index += 1
    }
    val name = path.substring(from, index)

    matchChar(RCURLY)

    new TemplateMatcher(name)
  }

  /**
    * Assume the index points to somewhere in-between named slots.
    * Collect  everything up to the next named slot (or the end of the input)
    */
  private def matchStaticText(): TextMatcher = {
    val from = index
    while ((index < path.length) && (path(index) != LCURLY)) {
      if (path(index) == RCURLY) {
        throw new IllegalArgumentException(s"Detected curly braces mismatch at ${from} and $index in $path")
      }

      index += 1
    }

    new TextMatcher(path.substring(from, index))
  }

  private def parse() : Seq[PathMatcher] = {
    while (index < path.length) {
      path(index) match {
        case LCURLY =>
          val matcher = matchTemplate()
          matchers += matcher

        case _ =>
          val matcher = matchStaticText()
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

  /** @return true if the path contains at least one parameter template such as "/{slot}" */
  def hasTemplates(path: String) : Boolean = path.contains(PathParser.LCURLY)

  /** Sequentially scan a URL template string. Split it into segments representing names slots and everything else. */
  def apply(path: String) : Seq[PathMatcher] = new PathParser(path).parse()
}
