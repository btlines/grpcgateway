package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import com.trueaccord.scalapb.GeneratedMessage
import com.trueaccord.scalapb.json.JsonFormat
import io.grpc.ManagedChannel
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext) extends ChannelInboundHandlerAdapter {

  def name: String

  def shutdown(): Unit =
    if (!channel.isShutdown) channel.shutdown()

  def supportsCall(method: HttpMethod, uri: String): Boolean
  def unaryCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = msg match {
    case req: FullHttpRequest =>
      val body = req.content().toString(StandardCharsets.UTF_8)
      if (supportsCall(req.method(), req.uri())) {
        unaryCall(req.method(), req.uri(), body)
          .map(JsonFormat.toJsonString)
          .map(_.getBytes(StandardCharsets.UTF_8))
          .onComplete {
            case Success(json) =>
              val res = new DefaultFullHttpResponse(
                req.protocolVersion(),
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(json)
              )
              res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
              HttpUtil.setContentLength(res, json.length)
              HttpUtil.setKeepAlive(res, HttpUtil.isKeepAlive(req))
              ctx.writeAndFlush(res)

            case Failure(e) =>
              val status = e match {
                case _: UnsupportedOperationException => HttpResponseStatus.NOT_FOUND
                case _: NoSuchElementException        => HttpResponseStatus.BAD_REQUEST
                case _                                => HttpResponseStatus.INTERNAL_SERVER_ERROR
              }
              val res = new DefaultHttpResponse(req.protocolVersion(), status)
              ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
          }
      } else {
        super.channelRead(ctx, msg)
      }
    case _ => super.channelRead(ctx, msg)
  }
}
