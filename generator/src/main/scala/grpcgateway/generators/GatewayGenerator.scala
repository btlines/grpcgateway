package grpcgateway.generators

import com.google.api.AnnotationsProto
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors._
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import com.trueaccord.scalapb.compiler.FunctionalPrinter.PrinterEndo
import com.trueaccord.scalapb.compiler.{DescriptorPimps, FunctionalPrinter}

import scala.collection.JavaConverters._
import scalapbshade.v0_6_7.com.trueaccord.scalapb.Scalapb

object GatewayGenerator extends protocbridge.ProtocCodeGenerator with DescriptorPimps {

  override val params = com.trueaccord.scalapb.compiler.GeneratorParams()

  override def run(requestBytes: Array[Byte]): Array[Byte] = {
    val registry = ExtensionRegistry.newInstance()
    Scalapb.registerAllExtensions(registry)
    AnnotationsProto.registerAllExtensions(registry)

    val b = CodeGeneratorResponse.newBuilder
    val request = CodeGeneratorRequest.parseFrom(requestBytes, registry)

    val fileDescByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
        case (acc, fp) =>
          val deps = fp.getDependencyList.asScala.map(acc)
          acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
      }

    request.getFileToGenerateList.asScala.foreach { name =>
      val fileDesc     = fileDescByName(name)
      val responseFile = generateFile(fileDesc)
      b.addFile(responseFile)
    }
    b.build.toByteArray
  }

  private def generateFile(fileDesc: FileDescriptor): CodeGeneratorResponse.File = {
    val b          = CodeGeneratorResponse.File.newBuilder()
    val objectName = fileDesc.fileDescriptorObjectName.substring(0, fileDesc.fileDescriptorObjectName.length - 5) + "Gateway"
    b.setName(s"${fileDesc.scalaDirectory}/$objectName.scala")

    val fp = FunctionalPrinter()
      .add(s"package ${fileDesc.scalaPackageName}")
      .newline
      .add(
        "import _root_.com.trueaccord.scalapb.GeneratedMessage",
        "import _root_.com.trueaccord.scalapb.json.JsonFormat",
        "import _root_.grpcgateway.handlers._",
        "import _root_.io.grpc._",
        "import _root_.io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}"
      )
      .newline
      .add(
        "import scala.collection.JavaConverters._",
        "import scala.concurrent.{ExecutionContext, Future}",
        "import com.trueaccord.scalapb.json.JsonFormatException",
        "import scala.util._"
      )
      .newline
      .print(fileDesc.getServices.asScala) { case (p, s) => generateService(s)(p) }
      .newline

    b.setContent(fp.result)
    b.build
  }

  private def generateService(service: ServiceDescriptor): PrinterEndo =
    _.add(s"class ${service.getName}Handler(channel: ManagedChannel)(implicit ec: ExecutionContext)").indent
      .add(
        "extends GrpcGatewayHandler(channel)(ec) {",
        s"""override val name: String = "${service.getName}"""",
        s"private val stub = ${service.getName}Grpc.stub(channel)"
      )
      .newline
      .call(generateSupportsCall(service))
      .newline
      .call(generateUnaryCall(service))
      .outdent
      .add("}")
      .newline

  private def getUnaryCallsWithHttpExtension(service: ServiceDescriptor) = {
    service.getMethods.asScala.filter { m =>
      // only unary calls with http method specified
      !m.isClientStreaming && !m.isServerStreaming && m.getOptions.hasExtension(AnnotationsProto.http)
    }
  }

  private def generateUnaryCall(service: ServiceDescriptor): PrinterEndo = { printer =>
    val methods = getUnaryCallsWithHttpExtension(service)
    printer
      .add(s"override def unaryCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage] = {")
      .indent
      .add(
        "val queryString = new QueryStringDecoder(uri)",
        "(method.name, queryString.path) match {"
      )
      .indent
      .print(methods) { case (p, m) => generateMethodHandlerCase(m)(p) }
      .add("case (methodName, path) => ")
      .addIndented("""Future.failed(InvalidArgument(s"No route defined for $methodName($path)"))""")
      .outdent
      .add("}")
      .outdent
      .add("}")
  }

  private def generateSupportsCall(service: ServiceDescriptor): PrinterEndo = { printer =>
    val methods = getUnaryCallsWithHttpExtension(service)
    printer
      .add(s"override def supportsCall(method: HttpMethod, uri: String): Boolean = {")
      .indent
      .add(
        "val queryString = new QueryStringDecoder(uri)",
        "(method.name, queryString.path) match {"
      )
      .indent
      .print(methods) { case (p, m) => generateMethodCase(m)(p) }
      .add("case _ => false")
      .outdent
      .add("}")
      .outdent
      .add("}")
  }

  private def generateMethodCase(method: MethodDescriptor): PrinterEndo = { printer =>
    val http = method.getOptions.getExtension(AnnotationsProto.http)
    http.getPatternCase match {
      case PatternCase.GET    => printer.add(s"""case ("GET", "${http.getGet}") => true""")
      case PatternCase.POST   => printer.add(s"""case ("POST", "${http.getPost}") => true""")
      case PatternCase.PUT    => printer.add(s"""case ("PUT", "${http.getPut}") => true""")
      case PatternCase.DELETE => printer.add(s"""case ("DELETE", "${http.getDelete}") => true""")
      case _                  => printer
    }
  }

  private def generateMethodHandlerCase(method: MethodDescriptor): PrinterEndo = { printer =>
    val http       = method.getOptions.getExtension(AnnotationsProto.http)
    val methodName = method.getName.charAt(0).toLower + method.getName.substring(1)
    http.getPatternCase match {
      case PatternCase.GET =>
        printer
          .add(s"""case ("GET", "${http.getGet}") => """)
          .indent
          .add("val input = Try {")
          .indent
          .call(generateInputFromQueryString(method.getInputType))
          .outdent
          .add("}")
          .add(s"Future.fromTry(input).flatMap(stub.$methodName)")
          .outdent
      case PatternCase.POST =>
        printer
          .add(s"""case ("POST", "${http.getPost}") => """)
          .add("for {")
          .addIndented(
            s"""msg <- Future.fromTry(Try(JsonFormat.fromJsonString[${method.getInputType.getName}](body)).recoverWith(jsonException2GatewayExceptionPF))""",
            s"res <- stub.$methodName(msg)"
          )
          .add("} yield res")
      case PatternCase.PUT =>
        printer
          .add(s"""case ("PUT", "${http.getPut}") => """)
          .add("for {")
          .addIndented(
            s"""msg <- Future.fromTry(JsonFormat.fromJsonString[${method.getInputType.getName}](body).recoverWith(jsonException2GatewayExceptionPF))""",
            s"res <- stub.$methodName(msg)"
          )
          .add("} yield res")
      case PatternCase.DELETE =>
        printer
          .add(s"""case ("DELETE", "${http.getDelete}") => """)
          .indent
          .add("val input = Try {")
          .indent
          .call(generateInputFromQueryString(method.getInputType))
          .outdent
          .add("}")
          .add(s"Future.fromTry(input).flatMap(stub.$methodName)")
          .outdent
      case _ => printer
    }
  }

  private def generateInputFromQueryString(d: Descriptor, prefix: String = ""): PrinterEndo = { printer =>
    val args = d.getFields.asScala.map(f => s"${f.getJsonName} = ${inputName(f, prefix)}").mkString(", ")

    printer
      .print(d.getFields.asScala) {
        case (p, f) =>
          f.getJavaType match {
            case JavaType.MESSAGE =>
              p.add(s"val ${inputName(f, prefix)} = {")
                .indent
                .call(generateInputFromQueryString(f.getMessageType, s"$prefix.${f.getJsonName}"))
                .outdent
                .add("}")
            case JavaType.ENUM =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""${f.getName}.valueOf(queryString.parameters().get("$prefix${f.getJsonName}").asScala.head)"""
                )
            case JavaType.BOOLEAN =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""queryString.parameters().get("$prefix${f.getJsonName}").asScala.head.toBoolean"""
                )
            case JavaType.DOUBLE =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""queryString.parameters().get("$prefix${f.getJsonName}").asScala.head.toDouble"""
                )
            case JavaType.FLOAT =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""queryString.parameters().get("$prefix${f.getJsonName}").asScala.head.toFloat"""
                )
            case JavaType.INT =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""queryString.parameters().get("$prefix${f.getJsonName}").asScala.head.toInt"""
                )
            case JavaType.LONG =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""queryString.parameters().get("$prefix${f.getJsonName}").asScala.head.toLong"""
                )
            case JavaType.STRING =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""queryString.parameters().get("$prefix${f.getJsonName}").asScala.head"""
                )
            case jt => throw new Exception(s"Unknown java type: $jt")
          }
      }
      .add(s"${d.getName}($args)")
  }

  private def inputName(d: FieldDescriptor, prefix: String = ""): String = {
    val name = prefix.split(".").filter(_.nonEmpty).map(s => s.charAt(0).toUpper + s.substring(1)).mkString + d.getName
    name.charAt(0).toLower + name.substring(1)
  }

}
