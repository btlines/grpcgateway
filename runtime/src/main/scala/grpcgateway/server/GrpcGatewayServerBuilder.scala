package grpcgateway.server

import grpcgateway.handlers.{GrpcGatewayHandler, MethodNotFoundHandler, SwaggerHandler}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}


case class GrpcGatewayServerBuilder(
  port: Int = 80,
  services: Seq[GrpcGatewayHandler] = Nil
) {

  def forPort(port: Int): GrpcGatewayServerBuilder = {
    copy(port = port)
  }

  def addService(service: GrpcGatewayHandler): GrpcGatewayServerBuilder = {
    copy(services = services :+ service)
  }

  def build(): GrpcGatewayServer = {
    val masterGroup = new NioEventLoopGroup()
    val slaveGroup = new NioEventLoopGroup()
    val bootstrap = new ServerBootstrap()
    bootstrap
      .group(masterGroup, slaveGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          ch.pipeline().addLast("codec", new HttpServerCodec())
          ch.pipeline().addLast("aggregator", new HttpObjectAggregator(512 * 1024))
          ch.pipeline().addLast("swagger", new SwaggerHandler(services))
          services.foreach { handler =>
            ch.pipeline().addLast(handler.name, handler)
          }
          ch.pipeline().addLast(new MethodNotFoundHandler())
        }
      })

    new GrpcGatewayServer(port, bootstrap, masterGroup, slaveGroup, services.toList)
  }

}

object GrpcGatewayServerBuilder {
  def forPort(port: Int): GrpcGatewayServerBuilder =
    new GrpcGatewayServerBuilder().forPort(port)
  def addService(service: GrpcGatewayHandler): GrpcGatewayServerBuilder =
    new GrpcGatewayServerBuilder().addService(service)
}
