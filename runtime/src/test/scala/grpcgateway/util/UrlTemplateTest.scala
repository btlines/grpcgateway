package grpcgateway.util

import org.scalatest.{Assertions, FlatSpec, Matchers}

class UrlTemplateTest extends FlatSpec with Matchers with Assertions {
  private val KEY = "k"
  private val VALUE = "v"
  private val PARAM1 = "T123"
  private val PARAM2 = "Param456"

  it should "preserve default semantics for fixed requests" in {
    val template = "/tree/trunk/branch/leaf/get"

    val url = UrlTemplate(template)
    val restful = url.matchUri(template).get
    assert(restful != null)

    val kvurl = UrlTemplate(template)
    val kvrestful = kvurl.matchUri(s"$template?$KEY=$VALUE").get
    assert(kvrestful.parameter(KEY) == VALUE)
  }

  it should "support multiple URL parameter templates" in {
    assertTwoParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/padding/{param}/"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/padding/$PARAM2/")

    assertTwoParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/{param}/suffix"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/$PARAM2/suffix")

    assertTwoParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/{param}"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/$PARAM2")

    assertTwoParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/{param}/"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/$PARAM2/")
  }

  it should "merge template and ordinary URL parameters" in {
    assertMixedParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/padding/{param}/"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/padding/$PARAM2/?$KEY=$VALUE")

    assertMixedParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/{param}/suffix"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/$PARAM2/suffix?$KEY=$VALUE")

    assertMixedParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/{param}"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/$PARAM2?$KEY=$VALUE")

    assertMixedParams(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/{param}/"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/$PARAM2/?$KEY=$VALUE")
  }

  it should "no match is found in mismatched URI" in {
    assertNoMatch(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}/padding/{param}/"),
      s"/tree/trunk/branch/leaf/get/$PARAM1/padding")

    assertNoMatch(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template}"),
      s"/tree/trunk/branch/leaf/get")

    assertNoMatch(
      UrlTemplate("/tree/trunk/branch/leaf/get/padding"),
      s"/tree/trunk/branch/leaf/get")
  }

  an [IllegalArgumentException] should be thrownBy {
    assertNoMatch(
      UrlTemplate("/tree/trunk/branch/leaf/get/{template/padding/{param}/"),
      s"IGNORED")
  }

  an [IllegalArgumentException] should be thrownBy {
    assertNoMatch(
      UrlTemplate("/tree/trunk/branch/leaf/get/template}/padding/{param}/"),
      s"IGNORED")
  }

  private def assertTwoParams(template: UrlTemplate, uri: String): Unit = {
    val restful = template.matchUri(uri).get
    assert(restful.parameter("template") == PARAM1)
    assert(restful.parameter("param") == PARAM2)
    assert(restful.parameter(KEY) == null)
  }

  private def assertMixedParams(template: UrlTemplate, uri: String): Unit = {
    val restful = template.matchUri(uri).get
    assert(restful.parameter("template") == PARAM1)
    assert(restful.parameter("param") == PARAM2)
    assert(restful.parameter(KEY) == VALUE)
    assert(restful.parameter("") == null)
  }

  private def assertNoMatch(template: UrlTemplate, uri: String): Unit = {
    assert(template.matchUri(uri).isEmpty)
  }
}