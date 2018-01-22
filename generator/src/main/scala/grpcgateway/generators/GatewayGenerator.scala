package grpcgateway.generators

import com.google.api.{AnnotationsProto, HttpRule}
import com.google.api.HttpRule.PatternCase
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors._
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import com.trueaccord.scalapb.compiler.FunctionalPrinter.PrinterEndo
import com.trueaccord.scalapb.compiler.{DescriptorPimps, FunctionalPrinter}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
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
        "import _root_.grpcgateway.handlers.GrpcGatewayHandler",
        "import _root_.grpcgateway.handlers.jsonException2GatewayExceptionPF",
        "import _root_.io.grpc.ManagedChannel",
        "import _root_.io.netty.handler.codec.http.HttpMethod"
      )
      .newline
      .add(
        "import scala.concurrent.{ExecutionContext, Future}",
        "import grpcgateway.util.{RestfulUrl, UrlTemplate}",
        "import scala.util.Try"
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
        "// a function that takes a RestfulUrl and produces a function that takes a request body and returns a response message",
        "type RestfulHandler = RestfulUrl => (String) => Future[GeneratedMessage]",
        "",
        s"""override val name: String = "${service.getName}"""",
        s"private val stub = ${service.getName}Grpc.stub(channel)"
      )
      .newline
      .call(generateCallSeqsByVerb(getUnaryCallsWithHttpExtension(service)))
      .outdent
      .add("}")
      .newline

  private def getUnaryCallsWithHttpExtension(service: ServiceDescriptor) = {
    service.getMethods.asScala.filter { m =>
      // only unary calls with http method specified
      !m.isClientStreaming && !m.isServerStreaming && m.getOptions.hasExtension(AnnotationsProto.http)
    }
  }

  private def generateMethodHandlerCase(method: MethodDescriptor): PrinterEndo = { printer =>
    val http       = method.getOptions.getExtension(AnnotationsProto.http)
    val methodName = method.getName.charAt(0).toLower + method.getName.substring(1)
    http.getPatternCase match {
      case PatternCase.GET =>
        printer
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
          .add("for {")
          .addIndented(
            s"""msg <- Future.fromTry(Try(JsonFormat.fromJsonString[${method.getInputType.getName}](body)).recoverWith(jsonException2GatewayExceptionPF))""",
            s"res <- stub.$methodName(msg)"
          )
          .add("} yield res")
      case PatternCase.PUT =>
        printer
          .add("for {")
          .addIndented(
            s"""msg <- Future.fromTry(Try(JsonFormat.fromJsonString[${method.getInputType.getName}](body)).recoverWith(jsonException2GatewayExceptionPF))""",
            s"res <- stub.$methodName(msg)"
          )
          .add("} yield res")
      case PatternCase.DELETE =>
        printer
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
                  s"""${f.getName}.valueOf(url.parameter("$prefix${f.getJsonName}"))"""
                )
            case JavaType.BOOLEAN =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""url.parameter("$prefix${f.getJsonName}").toBoolean"""
                )
            case JavaType.DOUBLE =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""url.parameter("$prefix${f.getJsonName}").toDouble"""
                )
            case JavaType.FLOAT =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""url.parameter("$prefix${f.getJsonName}").toFloat"""
                )
            case JavaType.INT =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""url.parameter("$prefix${f.getJsonName}").toInt"""
                )
            case JavaType.LONG =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""url.parameter("$prefix${f.getJsonName}").toLong"""
                )
            case JavaType.STRING =>
              p.add(s"val ${inputName(f, prefix)} = ")
                .addIndented(
                  s"""url.parameter("$prefix${f.getJsonName}")"""
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

  private def generateCallSeqsByVerb(descritors: mutable.Seq[MethodDescriptor]): PrinterEndo = { printer =>
    val verbToMethods: mutable.Map[PatternCase, Seq[RestfulMethod]] = MethodDescriptors.methodsByVerb(descritors)
    printer
      .call(generateCallSeqsByVerb(verbToMethods))
      .call(generateSupportsCall(verbToMethods.keySet))
  }

  private def generateCallSeqsByVerb(verbToMethods: mutable.Map[PatternCase, Seq[RestfulMethod]]): PrinterEndo = { printer =>
    printer.
      print(verbToMethods) { case (p, (pattern,methods)) => generateCallSeq(pattern, methods)(p) }
  }

  private def generateCallSeq(verb: PatternCase, methods: Seq[RestfulMethod]): PrinterEndo = { printer =>
    printer
      .add(s"private val ${verb.name().toLowerCase}Calls: Seq[(UrlTemplate, RestfulHandler)] = Seq(")
      .indent
      .print(methods) { case (p, method) => generateCall(method)(p) }
      .outdent
      .add(")") // Seq
      .newline
  }

  private def generateCall(method: RestfulMethod): PrinterEndo = { printer =>
    printer
      .add("(") // pair
      .add(
      s"""UrlTemplate("${method.urlTemplate}"),""",
      "(url: RestfulUrl) => (body: String) => {" // function
    )
      .indent
      .call(generateMethodHandlerCase(method.method))
      .outdent
      .add("}") // function
      .add("),") // pair
  }

  private def generateSupportsCall(verbs: collection.Set[PatternCase]): PrinterEndo = { printer =>
    printer
      .add(s"override def supportsCall(method: HttpMethod, uri: String): Option[UnaryCall] = {")
      .indent
      .add("method.name match {")
      .indent
      .print(verbs) { case (p, verb) => generateVerbCase(verb)(p) }
      .add("case _ => None")
      .outdent
      .add("}") // match
      .outdent
      .add("}") // def
  }

  private def generateVerbCase(verb: PatternCase): PrinterEndo = { printer =>
    printer
      .add(s"""case "${verb.name().toUpperCase}" =>""")
      .indent
      .add(s"for ((restful, handler) <- ${verb.name().toLowerCase}Calls) {")
      .indent
      .add("val mayBe = restful.matchUri(uri).map((url: RestfulUrl) => handler(url))")
      .add("if (mayBe.isDefined) {")
      .indent
      .add("return mayBe")
      .outdent
      .add("}") //if
      .outdent
      .add("}") // for
      .newline
      .add("None") // def
      .newline
      .outdent // case body
  }

}

private case class RestfulMethod(urlTemplate: String, method: MethodDescriptor)

private object MethodDescriptors {
  def methodsByVerb(descriptors: mutable.Seq[MethodDescriptor]) : mutable.Map[PatternCase, Seq[RestfulMethod]] = {
    val map = mutable.Map[PatternCase, ArrayBuffer[RestfulMethod]]()

    descriptors.foreach((md: MethodDescriptor) => {
      val http = md.getOptions.getExtension(AnnotationsProto.http)
      val seq = map.getOrElseUpdate(http.getPatternCase, ArrayBuffer())
      seq += RestfulMethod(urlTemplate(http), md)
    })

    map.asInstanceOf[mutable.Map[PatternCase, Seq[RestfulMethod]]] // todo how to do it with "A <:" ?
  }

  private def urlTemplate(http: HttpRule): String = {
    http.getPatternCase match {
      case PatternCase.GET => http.getGet
      case PatternCase.POST => http.getPost
      case PatternCase.PUT => http.getPut
      case PatternCase.DELETE => http.getDelete
      case _ => throw new IllegalArgumentException(s"Unsupported pattern: ${http.getPatternCase}")
    }
  }
}
