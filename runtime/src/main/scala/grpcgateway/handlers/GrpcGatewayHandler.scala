package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import com.trueaccord.scalapb.GeneratedMessage
import com.trueaccord.scalapb.json.JsonFormat
import io.grpc.Status.Code
import io.grpc.{ManagedChannel, StatusRuntimeException}
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
                case s: StatusRuntimeException =>
                  s.getStatus.getCode match {
                    case Code.OK                  => HttpResponseStatus.OK
                    case Code.CANCELLED           => HttpResponseStatus.GONE
                    case Code.UNKNOWN             => HttpResponseStatus.NOT_FOUND
                    case Code.INVALID_ARGUMENT    => HttpResponseStatus.BAD_REQUEST
                    case Code.DEADLINE_EXCEEDED   => HttpResponseStatus.GATEWAY_TIMEOUT
                    case Code.NOT_FOUND           => HttpResponseStatus.NOT_FOUND
                    case Code.ALREADY_EXISTS      => HttpResponseStatus.CONFLICT
                    case Code.PERMISSION_DENIED   => HttpResponseStatus.FORBIDDEN
                    case Code.RESOURCE_EXHAUSTED  => HttpResponseStatus.INSUFFICIENT_STORAGE
                    case Code.FAILED_PRECONDITION => HttpResponseStatus.PRECONDITION_FAILED
                    case Code.ABORTED             => HttpResponseStatus.GONE
                    case Code.OUT_OF_RANGE        => HttpResponseStatus.BAD_REQUEST
                    case Code.UNIMPLEMENTED       => HttpResponseStatus.NOT_IMPLEMENTED
                    case Code.INTERNAL            => HttpResponseStatus.INTERNAL_SERVER_ERROR
                    case Code.UNAVAILABLE         => HttpResponseStatus.NOT_ACCEPTABLE
                    case Code.DATA_LOSS           => HttpResponseStatus.PARTIAL_CONTENT
                    case Code.UNAUTHENTICATED     => HttpResponseStatus.UNAUTHORIZED
                  }
                case _ => HttpResponseStatus.INTERNAL_SERVER_ERROR
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
