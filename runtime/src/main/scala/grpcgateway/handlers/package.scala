package grpcgateway

import java.nio.charset.StandardCharsets

import io.grpc.Status.Code
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._

package object handlers {

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

  def buildFullHttpResponse(
   requestMsg: HttpMessage,
   responseBody: String,
   responseStatus: HttpResponseStatus,
   responseContentType: String
  ): FullHttpResponse = {

    val res = new DefaultFullHttpResponse(
      requestMsg.protocolVersion(),
      responseStatus,
      Unpooled.copiedBuffer(responseBody, StandardCharsets.UTF_8)
    )

    res.headers().set(HttpHeaderNames.CONTENT_TYPE, responseContentType)

    HttpUtil.setContentLength(res, responseBody.length)
    HttpUtil.setKeepAlive(res, HttpUtil.isKeepAlive(requestMsg))

    res

  }

}
