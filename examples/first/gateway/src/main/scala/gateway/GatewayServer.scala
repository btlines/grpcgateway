package gateway

import grpcgateway.server.GrpcGatewayServerBuilder

import scala.concurrent.ExecutionContext

case class GatewayServer(
  channelAddress: String,
  channelPort: Int,
  gwPort: Int
)(implicit ec: ExecutionContext) {

  val channel =
    io.grpc.ManagedChannelBuilder
      .forAddress(channelAddress, channelPort)
      .usePlaintext(true)
      .build()

  val gateway =
    GrpcGatewayServerBuilder
      .forPort(gwPort)
      .addService(new CalcServiceRestHandler(channel))
      .addService(new GreetServiceRestHandler(channel))
      .build()

}
