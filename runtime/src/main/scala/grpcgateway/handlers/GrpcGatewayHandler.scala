package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import com.trueaccord.scalapb.GeneratedMessage
import com.trueaccord.scalapb.json.JsonFormat
import io.grpc.{ ManagedChannel, StatusException, StatusRuntimeException }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.handler.codec.http._

import scala.concurrent.{ ExecutionContext, Future }

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext) extends ChannelInboundHandlerAdapter {

  def name: String

  def shutdown(): Unit =
    if (!channel.isShutdown) channel.shutdown()

  def supportsCall(method: HttpMethod, uri: String): Boolean
  def unaryCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {

    msg match {
      case req: FullHttpRequest =>

        if (supportsCall(req.method(), req.uri())) {

          val body = req.content().toString(StandardCharsets.UTF_8)

          unaryCall(req.method(), req.uri(), body)
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
                case e: StatusRuntimeException => e.getMessage -> toHttpCode(e.getStatus.getCode.value())
                case e: StatusException => e.getMessage -> toHttpCode(e.getStatus.getCode.value())
                case e: GatewayException => e.details -> toHttpCode(e.code)
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

  private def toHttpCode(code: Int) = {
    GRPC_HTTP_CODE_MAP.getOrElse(code, HttpResponseStatus.INTERNAL_SERVER_ERROR)
  }
}
