package grpcgateway

import java.nio.charset.StandardCharsets

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._

package object handlers {

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
