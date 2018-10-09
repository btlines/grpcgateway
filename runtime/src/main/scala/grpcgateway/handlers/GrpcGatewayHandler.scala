package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat
import io.grpc.ManagedChannel
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._

import scala.concurrent.{ExecutionContext, Future}

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext) extends ChannelInboundHandlerAdapter {
  /** a function that takes a request body and returns a response message */
  type UnaryCall = (String) => Future[GeneratedMessage]

  def name: String

  def shutdown(): Unit =
    if (!channel.isShutdown) channel.shutdown()

  /**
    * @param method HTTP verb
    * @param uri request path
    * @return response message
    */
  def supportsCall(method: HttpMethod, uri: String): Option[UnaryCall]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {

    msg match {
      case req: FullHttpRequest =>

        val mayBeCall: Option[UnaryCall] = supportsCall(req.method(), req.uri())

        if (mayBeCall.isDefined) {
          val unaryCall = mayBeCall.get
          val body = req.content().toString(StandardCharsets.UTF_8)

          unaryCall(body)
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
}
