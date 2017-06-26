package grpcgateway.handlers

import javax.activation.MimetypesFileTypeMap

import io.grpc.internal.IoUtils
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._

object SwaggerHandler {
  val SwaggerPath = "META-INF/resources/webjars/swagger-ui/3.0.10/"
  val DocsPrefix = "/docs/"
  val SpecsPrefix = "/specs/"
}

@Sharable
class SwaggerHandler extends ChannelInboundHandlerAdapter {
  import SwaggerHandler._
  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = msg match {
    case req: FullHttpRequest =>
      val queryString = new QueryStringDecoder(req.uri())
      val path = queryString.path()

      if (path.startsWith(DocsPrefix)) {
        val filename = SwaggerPath + path.substring(DocsPrefix.length)
        writeResource(ctx, req, filename)
      } else if (path.startsWith(SpecsPrefix)) {
        val filename = path.substring(1) // remove heading slash
        writeResource(ctx, req, filename)
      } else super.channelRead(ctx, msg)
    case _ => super.channelRead(ctx, msg)
  }

  private def writeResource(ctx: ChannelHandlerContext, req: FullHttpRequest, filename: String): Unit = {
    Thread.currentThread().getContextClassLoader.getResourceAsStream(filename) match {
      case null =>
        val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND)
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
      case resource =>
        val bytes = IoUtils.toByteArray(resource)
        val res = new DefaultFullHttpResponse(
          req.protocolVersion(),
          HttpResponseStatus.OK,
          Unpooled.copiedBuffer(bytes)
        )
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, new MimetypesFileTypeMap().getContentType(filename))
        HttpUtil.setContentLength(res, bytes.length)
        HttpUtil.setKeepAlive(res, HttpUtil.isKeepAlive(req))
        ctx.writeAndFlush(res)
    }
  }
}
