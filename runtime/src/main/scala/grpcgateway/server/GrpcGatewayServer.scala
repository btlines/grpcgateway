package grpcgateway.server

import grpcgateway.handlers.GrpcGatewayHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{ChannelFuture, EventLoopGroup}

class GrpcGatewayServer private[server] (
  port: Int,
  bootstrap: ServerBootstrap,
  masterGroup: EventLoopGroup,
  slaveGroup: EventLoopGroup,
  services: List[GrpcGatewayHandler]
) {
    private var channel: Option[ChannelFuture] = None

    def start(): Unit = {
      channel = Option(bootstrap.bind(port).sync())
    }

    def shutdown(): Unit = {
      slaveGroup.shutdownGracefully()
      masterGroup.shutdownGracefully()
      services.foreach(_.shutdown())
      channel.foreach(_.channel().closeFuture().sync())
    }
}
