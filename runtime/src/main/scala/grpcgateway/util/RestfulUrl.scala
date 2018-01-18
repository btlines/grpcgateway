package grpcgateway.util

import java.util

import RestfulUrl._

import scala.collection.JavaConverters._
import scala.collection.mutable

/** A container of extracted URI properties */
trait RestfulUrl {
  /** @return named URL parameter extracted from a query uri with a UrlTemplate */
  def parameter(name: String): String
}

private final class PlainRestfulUrl(parameters: PathParams) extends RestfulUrl {
  override def parameter(name: String): String = parameters.get(name).asScala.head
}

private final class MergedRestfulUrl(templateParams: TemplateParams, pathParams: PathParams) extends RestfulUrl {
  override def parameter(name: String): String = {
    if (templateParams.contains(name)) {
      templateParams(name)
    } else if (pathParams.containsKey(name)) {
      pathParams.get(name).asScala.head
    } else {
      null //throw new IllegalArgumentException(s"Property not found: $name")
    }
  }
}

private object RestfulUrl {
  type PathParams = util.Map[String, util.List[String]]
  type TemplateParams = mutable.Map[String, String]
}
