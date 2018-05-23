package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat
import io.grpc.ManagedChannel
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.handler.codec.http._

import scala.concurrent.{ ExecutionContext, Future }
import org.slf4j.{ Logger, LoggerFactory }

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext) extends ChannelInboundHandlerAdapter {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

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
                case e: GatewayException => e.details -> GRPC_HTTP_CODE_MAP.getOrElse(e.code, HttpResponseStatus.INTERNAL_SERVER_ERROR)
                case _ =>
                  logger.warn("channel read exception", err)
                  "Internal error" -> HttpResponseStatus.INTERNAL_SERVER_ERROR
              }

              buildFullHttpResponse(
                requestMsg = req,
                responseBody = body,
                responseStatus = status
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
}
