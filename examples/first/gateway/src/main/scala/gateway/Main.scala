package gateway

import grpcgateway.server.GrpcGatewayServerBuilder
import hellogrpc.gateway.{ CalcServiceRestHandler, GreetServiceRestHandler }

import scala.concurrent.ExecutionContext

object Main extends App {

  implicit val ec = ExecutionContext.Implicits.global

  val channel =
    io.grpc.ManagedChannelBuilder
      .forAddress("localhost", 9095)
      .usePlaintext(true)
      .build()

  val gateway =
    GrpcGatewayServerBuilder
      .forPort(9097)
      .addService(new CalcServiceRestHandler(channel))
      .addService(new GreetServiceRestHandler(channel))
      .build()

  gateway.start()

  sys.addShutdownHook {
    println("shutting down gateway")
  }

  println("gateway started")

}
