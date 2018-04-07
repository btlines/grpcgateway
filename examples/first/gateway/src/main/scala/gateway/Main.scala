package gateway

import grpcgateway.server.GrpcGatewayServerBuilder
import hellogrpc.calc.CalcServiceHandler
import hellogrpc.greet.GreetServiceHandler

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
      .addService(new CalcServiceHandler(channel))
      .addService(new GreetServiceHandler(channel))
      .build()

  gateway.start()

  sys.addShutdownHook {
    println("shutting down gateway")
  }

  println("gateway started")

}