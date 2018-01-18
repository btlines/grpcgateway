package grpcgateway.generators

import java.nio.file.{Files, Paths}

import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import org.scalatest.{Assertions, FlatSpec}
import protocbridge.frontend.PluginFrontend

class GatewayGeneratorTest extends FlatSpec with Assertions {
  private val DIR = "generator/target/scala-2.12/test-classes/"

  it should "generate" in {
    val requestProtoStream = Files.newInputStream(Paths.get(DIR + "post_request_proto.bin"))
    val request = CodeGeneratorRequest.parseFrom(requestProtoStream)

    val responseBytes: Array[Byte] = PluginFrontend.runWithBytes(GatewayGenerator, request.toByteArray)
    val generatedResponse = CodeGeneratorResponse.parseFrom(responseBytes)

    for (i <- 0 until generatedResponse.getFileCount) {
      val file  = generatedResponse.getFile(i)
      Files.write(Paths.get(DIR + file.getName.substring(file.getName.lastIndexOf("/") + 1)), file.getContent.getBytes())
    }
  }
}
