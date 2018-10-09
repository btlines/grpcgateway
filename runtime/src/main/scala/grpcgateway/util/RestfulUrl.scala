package grpcgateway.util

import java.util

import RestfulUrl._

import scala.collection.JavaConverters._
import scala.collection.mutable

/** A container of extracted URI properties */
trait RestfulUrl {
  /**
    * A uniform way to access URL parameters extracted from named slots (e.g. "/{slot}/") and ordinary parameters (e.g. "?k=v")
    * @return named URL parameter extracted from a query uri with a UrlTemplate */
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
  /** parameters extracted from named slots such as "/{slot}/" */
  type PathParams = util.Map[String, util.List[String]]

  /** ordinary parameters such as "?k=v" */
  type TemplateParams = mutable.Map[String, String]
}
