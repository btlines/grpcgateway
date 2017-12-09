package grpcgateway.handlers

import java.nio.charset.StandardCharsets

import com.trueaccord.scalapb.GeneratedMessage
import com.trueaccord.scalapb.json.JsonFormat
import io.grpc.Status.Code
import io.grpc.{ManagedChannel, StatusRuntimeException}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods

import scala.concurrent.{ExecutionContext, Future}

object GrpcGatewayHandler {

  sealed trait GrpcGatewayHandlerError extends Exception

  case class NotSupportedMethod(route: String) extends GrpcGatewayHandlerError
  
  val GRPC_HTTP_CODE_MAP: Map[Int, HttpResponseStatus] = Map(
    Code.OK.value()                  -> HttpResponseStatus.OK,
    Code.CANCELLED.value()           -> HttpResponseStatus.GONE,
    Code.UNKNOWN.value()             -> HttpResponseStatus.NOT_FOUND,
    Code.INVALID_ARGUMENT.value()    -> HttpResponseStatus.BAD_REQUEST,
    Code.DEADLINE_EXCEEDED.value()   -> HttpResponseStatus.GATEWAY_TIMEOUT,
    Code.NOT_FOUND.value()           -> HttpResponseStatus.NOT_FOUND,
    Code.ALREADY_EXISTS.value()      -> HttpResponseStatus.CONFLICT,
    Code.PERMISSION_DENIED.value()   -> HttpResponseStatus.FORBIDDEN,
    Code.RESOURCE_EXHAUSTED.value()  -> HttpResponseStatus.INSUFFICIENT_STORAGE,
    Code.FAILED_PRECONDITION.value() -> HttpResponseStatus.PRECONDITION_FAILED,
    Code.ABORTED.value()             -> HttpResponseStatus.GONE,
    Code.OUT_OF_RANGE.value()        -> HttpResponseStatus.BAD_REQUEST,
    Code.UNIMPLEMENTED.value()       -> HttpResponseStatus.NOT_IMPLEMENTED,
    Code.INTERNAL.value()            -> HttpResponseStatus.INTERNAL_SERVER_ERROR,
    Code.UNAVAILABLE.value()         -> HttpResponseStatus.NOT_ACCEPTABLE,
    Code.DATA_LOSS.value()           -> HttpResponseStatus.PARTIAL_CONTENT,
    Code.UNAUTHENTICATED.value()     -> HttpResponseStatus.UNAUTHORIZED
  )

  def throwable2HttpResponseStatus(e: Throwable): HttpResponseStatus = {
    e match {
      case _: UnsupportedOperationException => HttpResponseStatus.NOT_FOUND
      case _: NoSuchElementException        => HttpResponseStatus.BAD_REQUEST
      case s: StatusRuntimeException => val code = s.getStatus.getCode.value(); GRPC_HTTP_CODE_MAP.getOrElse(code, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      case _ => HttpResponseStatus.INTERNAL_SERVER_ERROR
    }
  }

}

@Sharable
abstract class GrpcGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext) extends ChannelInboundHandlerAdapter {

  import GrpcGatewayHandler._

  def name: String

  def shutdown(): Unit =
    if (!channel.isShutdown) channel.shutdown()

  def supportsCall(method: HttpMethod, uri: String): Boolean
  def unaryCall(method: HttpMethod, uri: String, body: JValue): Future[GeneratedMessage]

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {

    msg match {
      case req: FullHttpRequest =>

        if (supportsCall(req.method(), req.uri())) {

          val f = for {
            json <- Future {
              val body = req.content().toString(StandardCharsets.UTF_8)
              JsonMethods.parse(body)
            }.recover { case err => throw new Exception(s"Wrong json payload: ${err.getMessage}") }
            resp <- {
              unaryCall(req.method(), req.uri(), json)
                .map(JsonFormat.toJsonString)
                .map(_.getBytes(StandardCharsets.UTF_8))
                .map(json => {
                  buildFullHttpResponse(
                    requestMsg = req,
                    responseBody = new String(json, StandardCharsets.UTF_8),
                    responseStatus = HttpResponseStatus.OK,
                    responseContentType = "application/json"
                  )
                })
            }
          } yield resp

          f.recover({ case err =>
            buildFullHttpResponse(
              requestMsg = req,
              responseBody = err.getMessage,
              responseStatus = throwable2HttpResponseStatus(err),
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
