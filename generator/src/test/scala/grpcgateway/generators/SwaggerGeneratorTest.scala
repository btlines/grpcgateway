package grpcgateway.generators

import com.google.protobuf.descriptor.FileDescriptorProto
import com.trueaccord.scalapb.TextFormat
import org.scalatest.FreeSpec

import scala.io.Source

class SwaggerGeneratorTest extends FreeSpec {

  "SwaggerGenerator" - {

    "extractDefs" - {

      "case 1" in {

        val raw = Source.fromURL(getClass.getResource("/SimpleMsg.proto")).mkString

        val fileProto = TextFormat.fromAscii(FileDescriptorProto, raw)

        val a = 1

      }

    }

  }

}
