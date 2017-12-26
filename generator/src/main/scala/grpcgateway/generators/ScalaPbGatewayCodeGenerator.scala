package grpcgateway.generators

import com.google.api.AnnotationsProto
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}

import scalapbshade.v0_6_7.com.trueaccord.scalapb.Scalapb

class ScalaPbGatewayCodeGenerator extends protocbridge.ProtocCodeGenerator {

  override def run(requestBytes: Array[Byte]): Array[Byte] = {
    val registry = ExtensionRegistry.newInstance()
    Scalapb.registerAllExtensions(registry)
    AnnotationsProto.registerAllExtensions(registry)

    val b = CodeGeneratorResponse.newBuilder
    val request = CodeGeneratorRequest.parseFrom(requestBytes, registry)
    request.getParameter

    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc)
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
      }

    for {
      fileDesc <- request.getFileToGenerateList.asScala.map(fileDescByName)
      serviceDesc <- fileDesc.getServices.asScala
    } b.addFile(generateServiceFile(serviceDesc, fileDesc))

    b.build.toByteArray
  }

}
