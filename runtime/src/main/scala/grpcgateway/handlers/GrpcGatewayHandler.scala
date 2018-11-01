package grpcgateway.handlers

import io.grpc.{ManagedChannel, StatusException, StatusRuntimeException}
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import scalapb.GeneratedMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

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
          val body = Try({
            val buf = req.content()
            val bytes = new Array[Byte](buf.readableBytes())

            try(buf.readBytes(bytes))
            finally {
              buf.release()
            }
            new String(bytes)
          }).recoverWith({
            case ex : Throwable =>
              ex.printStackTrace()
              Failure(ex)
          }).getOrElse("")

          unaryCall(body)
            .map(
              new scalapb.json4s.Printer(
                includingDefaultValueFields = true,
                preservingProtoFieldNames = false,
                formattingLongAsNumber = false
              ).print
            )
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
              case e: StatusException => e.getStatus.getDescription -> GRPC_HTTP_CODE_MAP.getOrElse(e.getStatus.getCode.value(), HttpResponseStatus.INTERNAL_SERVER_ERROR)
              case e: StatusRuntimeException => e.getStatus.getDescription -> GRPC_HTTP_CODE_MAP.getOrElse(e.getStatus.getCode.value(), HttpResponseStatus.INTERNAL_SERVER_ERROR)
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
