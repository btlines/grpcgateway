package grpcgateway.handlers

import java.nio.file.{Path, Paths}
import javax.activation.MimetypesFileTypeMap

import io.grpc.internal.IoUtils
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil

object SwaggerHandler {
  val SwaggerUiPath: Path   = Paths.get("META-INF/resources/webjars/swagger-ui/3.5.0")
  val SpecsPrefix: Path     = Paths.get("/specs/")
  val DocsPrefix: Path      = Paths.get("/docs/")
  val DocsLandingPage: Path = Paths.get("/docs/index.html")
  val RootPath: Path        = Paths.get("/")
}

@Sharable
class SwaggerHandler(services: Seq[GrpcGatewayHandler]) extends ChannelInboundHandlerAdapter {
  import SwaggerHandler._
  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = msg match {
    case req: FullHttpRequest =>
      val queryString = new QueryStringDecoder(req.uri())
      val path        = Paths.get(queryString.path())
      val res = path match {
        case RootPath        => Some(createRedirectResponse(req, DocsLandingPage))
        case DocsPrefix      => Some(createRedirectResponse(req, DocsLandingPage))
        case DocsLandingPage => Some(createStringResponse(req, indexPage.toString()))
        case p if p.startsWith(DocsPrefix) => {
          // swagger UI loading its own resources
          val resourcePath = SwaggerUiPath.resolve(RootPath.relativize(path).subpath(1, path.getNameCount))
          Some(createResourceResponse(req, resourcePath))
        }
        case p if p.startsWith(SpecsPrefix) =>
          // swagger UI loading up spec file
          Some(createResourceResponse(req, RootPath.relativize(path)))
        case _ => None
      }
      res match {
        case Some(response) => ctx.writeAndFlush(response)
        case None           => super.channelRead(ctx, msg)
      }
    case _ => super.channelRead(ctx, msg)
  }

  private def createRedirectResponse(req: FullHttpRequest, path: Path) = {
    val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.TEMPORARY_REDIRECT)
    res.headers().add(HttpHeaderNames.LOCATION, path.toString)
    res
  }

  private def createStringResponse(req: FullHttpRequest, value: String) = {
    val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK, Unpooled.copiedBuffer(value, CharsetUtil.UTF_8))
    res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html")
    setCommonHeaders(req, res)
    res
  }

  private def createResourceResponse(req: FullHttpRequest, path: Path) = {
    val resource = Thread.currentThread().getContextClassLoader.getResourceAsStream(separatorsToUnix(path.toString))
    val res = resource match {
      case null => new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND)
      case some =>
        val bytes = IoUtils.toByteArray(some)
        val res = new DefaultFullHttpResponse(
          req.protocolVersion(),
          HttpResponseStatus.OK,
          Unpooled.copiedBuffer(bytes)
        )
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypes.getContentType(separatorsToUnix(path.toString)))
        setCommonHeaders(req, res)
        res
    }
    res
  }

  private def setCommonHeaders(req: FullHttpRequest, res: FullHttpResponse) = {
    HttpUtil.setContentLength(res, res.content().readableBytes())
    HttpUtil.setKeepAlive(res, HttpUtil.isKeepAlive(req))
  }

  private def separatorsToUnix(path: String) = path.replace('\\', '/')

  private val mimeTypes = new MimetypesFileTypeMap()
  mimeTypes.addMimeTypes("image/png png PNG")
  mimeTypes.addMimeTypes("text/css css CSS")
  private val serviceUrls  = services.map(s => s"{url: '/specs/${s.name}.yml', name: '${s.name}'}").mkString(", ")
  private val serviceNames = services.map(s => s.name).mkString(", ")
  private val indexPage =
    <html lang="en">
      <head>
          <meta charset="UTF-8"/>
          <title>Swagger UI: {serviceNames}</title>
          <link href="https://fonts.googleapis.com/css?family=Open+Sans:400,700Source+Code+Pro:300,600Titillium+Web:400,600,700" rel="stylesheet"/>
          <link rel="stylesheet" type="text/css" href="./swagger-ui.css" />
          <link rel="icon" type="image/png" href="./favicon-32x32.png" sizes="32x32" />
          <link rel="icon" type="image/png" href="./favicon-16x16.png" sizes="16x16" />
          <style>
              html
              {{
                  box-sizing: border-box;
                  overflow: -moz-scrollbars-vertical;
                  overflow-y: scroll;
              }}
              *,
              *:before,
              *:after
              {{
                  box-sizing: inherit;
              }}
              body {{
                  margin:0;
                  background: #fafafa;
              }}
          </style>
       </head>
      <body>
        <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" style="position:absolute;width:0;height:0">
            <defs>
                <symbol viewBox="0 0 20 20" id="unlocked">
                    <path d="M15.8 8H14V5.6C14 2.703 12.665 1 10 1 7.334 1 6 2.703 6 5.6V6h2v-.801C8 3.754 8.797 3 10 3c1.203 0 2 .754 2 2.199V8H4c-.553 0-1 .646-1 1.199V17c0 .549.428 1.139.951 1.307l1.197.387C5.672 18.861 6.55 19 7.1 19h5.8c.549 0 1.428-.139 1.951-.307l1.196-.387c.524-.167.953-.757.953-1.306V9.199C17 8.646 16.352 8 15.8 8z"></path>
                </symbol>
                <symbol viewBox="0 0 20 20" id="locked">
                    <path d="M15.8 8H14V5.6C14 2.703 12.665 1 10 1 7.334 1 6 2.703 6 5.6V8H4c-.553 0-1 .646-1 1.199V17c0 .549.428 1.139.951 1.307l1.197.387C5.672 18.861 6.55 19 7.1 19h5.8c.549 0 1.428-.139 1.951-.307l1.196-.387c.524-.167.953-.757.953-1.306V9.199C17 8.646 16.352 8 15.8 8zM12 8H8V5.199C8 3.754 8.797 3 10 3c1.203 0 2 .754 2 2.199V8z"/>
                </symbol>
                <symbol viewBox="0 0 20 20" id="close">
                    <path d="M14.348 14.849c-.469.469-1.229.469-1.697 0L10 11.819l-2.651 3.029c-.469.469-1.229.469-1.697 0-.469-.469-.469-1.229 0-1.697l2.758-3.15-2.759-3.152c-.469-.469-.469-1.228 0-1.697.469-.469 1.228-.469 1.697 0L10 8.183l2.651-3.031c.469-.469 1.228-.469 1.697 0 .469.469.469 1.229 0 1.697l-2.758 3.152 2.758 3.15c.469.469.469 1.229 0 1.698z"/>
                </symbol>
                <symbol viewBox="0 0 20 20" id="large-arrow">
                    <path d="M13.25 10L6.109 2.58c-.268-.27-.268-.707 0-.979.268-.27.701-.27.969 0l7.83 7.908c.268.271.268.709 0 .979l-7.83 7.908c-.268.271-.701.27-.969 0-.268-.269-.268-.707 0-.979L13.25 10z"/>
                </symbol>
                <symbol viewBox="0 0 20 20" id="large-arrow-down">
                    <path d="M17.418 6.109c.272-.268.709-.268.979 0s.271.701 0 .969l-7.908 7.83c-.27.268-.707.268-.979 0l-7.908-7.83c-.27-.268-.27-.701 0-.969.271-.268.709-.268.979 0L10 13.25l7.418-7.141z"/>
                </symbol>
                <symbol viewBox="0 0 24 24" id="jump-to">
                    <path d="M19 7v4H5.83l3.58-3.59L8 6l-6 6 6 6 1.41-1.41L5.83 13H21V7z"/>
                </symbol>
                <symbol viewBox="0 0 24 24" id="expand">
                    <path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"/>
                </symbol>
            </defs>
        </svg>
        <div id="swagger-ui"></div>
        <script src="./swagger-ui-bundle.js"></script>
        <script src="./swagger-ui-standalone-preset.js"></script>
        <script>
            window.onload = function() {{
                const ui = SwaggerUIBundle({{
                    urls: [{ serviceUrls }],
                    dom_id: '#swagger-ui',
                    deepLinking: true,
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    plugins: [
                        SwaggerUIBundle.plugins.DownloadUrl
                    ],
                    layout: 'StandaloneLayout',
                    validatorUrl: ''
                }})
                window.ui = ui
            }}
        </script>
      </body>
    </html>

}
