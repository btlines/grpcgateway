package gateway

import grpcgateway.server.GrpcGatewayServerBuilder
import hellogrpc.gateway._

import scala.concurrent.ExecutionContext

case class GatewayServer(
  channelAddress: String,
  channelPort: Int,
  gwPort: Int
)(implicit ec: ExecutionContext) {



}
