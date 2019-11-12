package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http._
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat
import scala.collection.JavaConverters._

import grpcgateway.handlers.GrpcGatewayHandler.RestToGrpcPassThroughHeaders


@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext) extends ChannelInboundHandlerAdapter {

  def name: String

  def shutdown(): Unit =
    if (!channel.isShutdown) channel.shutdown()

  def supportsCall(method: HttpMethod, uri: String): Boolean
  def unaryCall(method: HttpMethod, uri: String, headers: HttpHeaders, body: String): Future[GeneratedMessage]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {

    msg match {
      case req: FullHttpRequest =>
        if (supportsCall(req.method(), req.uri())) {

          val body = req.content().toString(StandardCharsets.UTF_8)

          unaryCall(req.method(), req.uri(), req.headers(), body)
            .map(JsonFormat.toJsonString)
            .map(json => {
              buildFullHttpResponse(
                requestMsg = req,
                responseBody = json,
                responseStatus = HttpResponseStatus.OK,
                responseContentType = "application/json"
              )
            })
            .recover({ case err =>

              val (body, status) = err match {
                case e: GatewayException => e.details -> GRPC_HTTP_CODE_MAP.getOrElse(e.code, HttpResponseStatus.INTERNAL_SERVER_ERROR)
                case _ => "Internal error" -> HttpResponseStatus.INTERNAL_SERVER_ERROR
              }

              buildFullHttpResponse(
                requestMsg = req,
                responseBody = body,
                responseStatus = status,
                responseContentType = "application/text"
              )
            }).foreach(resp => {
              ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
            })

        } else {
          super.channelRead(ctx, msg)
        }
      case _ => super.channelRead(ctx, msg)
    }
  }

  protected def stubWithHeaders[StubType <: AbstractStub[StubType]](stub: StubType, headers: HttpHeaders): StubType = {
    val passThroughHeaders =
      if (RestToGrpcPassThroughHeaders.isEmpty) Seq.empty else {
        headers.entries().asScala.collect {
          case entry if RestToGrpcPassThroughHeaders.contains(entry.getKey) =>
            (entry.getKey, entry.getValue)
        }
      }

    if (passThroughHeaders.isEmpty) stub else {
      val metadata = new Metadata()
      passThroughHeaders.foreach { case (headerName, headerValue) =>
        val metadataKey = Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER)
        metadata.put(metadataKey, headerValue)
      }
      MetadataUtils.attachHeaders[StubType](stub, metadata)
    }
  }
}

object GrpcGatewayHandler {
  private val RestToGrpcPassThroughHeaders: Set[String] = {
    val envVarName = "REST_TO_GRPC_PASS_THROUGH_HEADERS"

    Option(System.getenv(envVarName))
      .map { str =>
        val headers = str.split("""\s*,\s*""").toSet

        val asciiEncoder = StandardCharsets.US_ASCII.newEncoder()
        headers.foreach { header =>
          if (header.isEmpty) {
            throw new IllegalArgumentException(s"The empty string is not a legal header name; cannot process '$str' loaded from env variable $envVarName")
          } else if (!asciiEncoder.canEncode(header)) {
            throw new IllegalArgumentException(
              s"Found non ASCII header '$header' in headers loaded from env variable $envVarName. Only ASCII headers are supported")
          }
        }

        headers
      }
  }.getOrElse(Set.empty)
}
