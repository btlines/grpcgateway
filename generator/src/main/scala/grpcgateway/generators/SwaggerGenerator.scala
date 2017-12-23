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
import scala.collection.mutable
import scalapbshade.v0_6_7.com.trueaccord.scalapb.Scalapb

object SwaggerGenerator extends protocbridge.ProtocCodeGenerator with DescriptorPimps {

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

    for {
      fileDesc <- request.getFileToGenerateList.asScala.map(fileDescByName)
      serviceDesc <- fileDesc.getServices.asScala
    } b.addFile(generateFile(serviceDesc, fileDesc))

    b.build.toByteArray
  }

  private def generateFile(serviceDesc: ServiceDescriptor, fileDesc: FileDescriptor): CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()

    b.setName(s"${serviceDesc.getName}.yml")

    val methods = serviceDesc
      .getMethods.asScala
      .filter { m =>
        // only unary calls with http method specified
        !m.isClientStreaming && !m.isServerStreaming && m.getOptions.hasExtension(AnnotationsProto.http)
      }
    val paths = methods.groupBy(extractPath)
    val definitions = methods.flatMap(m => extractDefs(m.getInputType) ++ extractDefs(m.getOutputType)).toSet

    val fp = FunctionalPrinter()
      .add("swagger: '2.0'", "info:")
      .addIndented(
        "version: not set",
        s"title: '${serviceDesc.getName}'",
        s"description: 'REST API generated from ${fileDesc.getFile.getName}'"
      )
      .add("schemes:")
      .addIndented("- http", "- https")
      .add("consumes:")
      .addIndented("- 'application/json'")
      .add("produces:")
      .addIndented("- 'application/json'")
      .add("paths:")
      .indent
      .print(paths) { case (p, m) => generatePath(m)(p) }
      .outdent
      .add("definitions:")
      .indent
      .print(definitions) { case (p, d) => generateDefinition(d)(p) }
      .outdent

    b.setContent(fp.result)
    b.build
  }

  private def extractPath(m: MethodDescriptor): String = {
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    http.getPatternCase match {
      case PatternCase.GET => http.getGet
      case PatternCase.POST => http.getPost
      case PatternCase.PUT => http.getPut
      case PatternCase.DELETE => http.getDelete
      case PatternCase.PATCH => http.getPatch
      case _ => ""
    }
  }

  private def generatePath(pathMethods: (String, Seq[MethodDescriptor])): PrinterEndo = { printer =>
    pathMethods match {
      case (path, methods) =>
        printer
          .add(s"$path:")
          .indent
          .print(methods) { case (p, m) => generateMethod(m)(p) }
          .outdent
    }
  }

  private def generateMethod(m: MethodDescriptor): PrinterEndo = { printer =>
    val http = m.getOptions.getExtension(AnnotationsProto.http)
    http.getPatternCase match {
      case PatternCase.GET =>
        printer
          .add("get:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateQueryParameters(m.getInputType))
          .outdent
      case PatternCase.POST =>
        printer
          .add("post:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateBodyParameters(m.getInputType))
          .outdent
      case PatternCase.PUT =>
        printer
          .add("put:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateBodyParameters(m.getInputType))
          .outdent
      case PatternCase.DELETE =>
        printer
          .add("delete:")
          .indent
          .call(generateMethodInfo(m))
          .call(generateQueryParameters(m.getInputType))
          .outdent
      case _ => printer
    }
  }

  private def generateMethodInfo(m: MethodDescriptor): PrinterEndo =
    _.add("tags:")
      .addIndented(s"- ${m.getService.getName}")
      .add("summary:")
      .addIndented(s"'${m.getName}'")
      .add("description:")
      .addIndented(s"'Generated from ${m.getFullName}'")
      .add("produces:")
      .addIndented("['application/json']")
      .add("responses:")
      .indent
      .add("200:")
      .indent
      .add("description: 'Normal response'")
      .add("schema:")
      .addIndented(s"""$$ref: "#/definitions/${m.getOutputType.getName}"""")
      .outdent
      .outdent

  private def generateBodyParameters(inputType: Descriptor): PrinterEndo =
    _.add("parameters:").indent
      .add("- in: 'body'")
      .indent
      .add("name: body")
      .add("schema:")
      .addIndented(s"""$$ref: "#/definitions/${inputType.getName}"""")
      .outdent
      .outdent

  private def generateQueryParameters(inputType: Descriptor, prefix: String = ""): PrinterEndo =
    _.when(inputType.getFields.asScala.nonEmpty)(
      _.add("parameters:")
        .print(inputType.getFields.asScala) {
          case (p, f) =>
            p.call(generateQueryParameter(f))
        })

  private def generateQueryParameter(field: FieldDescriptor, prefix: String = ""): PrinterEndo = { printer =>
    field.getJavaType match {
      case JavaType.MESSAGE =>
        printer.call(
          generateQueryParameters(field.getMessageType, s"$prefix.${field.getJsonName}")
        )
      case JavaType.ENUM =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", s"type: string", "enum:")
          .addIndented(field.getEnumType.getValues.asScala.map(v => s"- ${v.getName}"): _*)
      case JavaType.INT =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", "type: integer", "format: int32")
      case JavaType.LONG =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", "type: integer", "format: int64")
      case t =>
        printer
          .add(s"- name: $prefix${field.getJsonName}")
          .addIndented("in: query", s"type: ${t.name.toLowerCase}")
    }
  }

  private def generateDefinition(d: Descriptor): PrinterEndo = {
    _.add(d.getName + ":").indent
      .add("type: object")
      .when(d.getFields.asScala.nonEmpty)(_.add("properties: ").indent
        .print(d.getFields.asScala) {
          case (printer, field) =>
            if (field.isRepeated) {
              printer
                .add(field.getJsonName + ":")
                .indent
                .add("type: array", "items:")
                .indent
                .call(generateDefinitionType(field))
                .outdent
                .outdent
            } else {
              printer
                .add(field.getJsonName + ":")
                .indent
                .call(generateDefinitionType(field))
                .outdent
            }
        }
        .outdent)
      .outdent
  }

  private def generateDefinitionType(field: FieldDescriptor): PrinterEndo = {
    field.getJavaType match {
      case JavaType.MESSAGE => _.add(s"""$$ref: "#/definitions/${field.getMessageType.getName}"""")
      case JavaType.ENUM =>
        _.add("type: string", "enum:").add(
          field.getEnumType.getValues.asScala.map(v => s"- ${v.getName}"): _*
        )
      case JavaType.INT  => _.add("type: integer", "format: int32")
      case JavaType.LONG => _.add("type: integer", "format: int64")
      case t             => _.add(s"type: ${t.name.toLowerCase}")
    }
  }

  private def extractDefs(d: Descriptor): Set[Descriptor] = {
    val explored: mutable.Set[Descriptor] = mutable.Set.empty
    def extractDefsRec(d: Descriptor): Set[Descriptor] = {
      if (explored.contains(d)) Set()
      else {
        explored.add(d)
        Set(d) ++ d.getFields.asScala.flatMap { f =>
          f.getJavaType match {
            case JavaType.MESSAGE => extractDefsRec(f.getMessageType)
            case _                => Set.empty[Descriptor]
          }
        }
      }
    }
    extractDefsRec(d)
  }
}
