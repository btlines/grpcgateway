import grpcgateway.generators.GatewayGenerator
import org.scalatest.{FlatSpec, Matchers}
import grpcgateway.test.SimpleServiceGrpc

import scalapb.compiler.{DescriptorPimps, FunctionalPrinter, GeneratorParams}

class GatewayGeneratorSpec extends FlatSpec with Matchers with DescriptorPimps {

  val params = GeneratorParams()

  val gen = new GatewayGenerator()

  "getPackageName" should "work for Test.proto" in {

    gen.getPackageName(SimpleServiceGrpc.javaDescriptor) shouldBe List("grpcgateway", "test", "Test")

  }

  "generateUnaryCall" should "work for Test.proto" in {

    val expected =
      """case ("GET", "/api/concatWords") =>
        |  val input = Try {
        |    val word1 =
        |      queryString.parameters().get("word1").asScala.head
        |    val word2 =
        |      queryString.parameters().get("word2").asScala.head
        |    ConcatWords(word1 = word1, word2 = word2)
        |  }
        |  Future.fromTry(input).flatMap(stub.concatWords)""".stripMargin

    val method = SimpleServiceGrpc.javaDescriptor.findMethodByName("concatWords")

    gen.generateMethodHandlerCase(method)(FunctionalPrinter()).result() shouldBe expected


  }

}
