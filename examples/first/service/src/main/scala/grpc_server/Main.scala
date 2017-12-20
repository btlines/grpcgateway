package grpc_server

import grpc_server.service.{ GreetServiceImpl, SumServiceImpl }
import hellogrpc.calc.CalcService.CalcServiceGrpc
import hellogrpc.greet.GreetService.GreetServiceGrpc
import io.grpc.{ Server, ServerBuilder }

import scala.concurrent.ExecutionContext

object Main extends App {

  implicit val ec = ExecutionContext.Implicits.global

  val server: Server = ServerBuilder
    .forPort(9095)
    .addService(CalcServiceGrpc.bindService(new SumServiceImpl, ec))
    .addService(GreetServiceGrpc.bindService(new GreetServiceImpl, ec))
    .build().start()

  sys.addShutdownHook(() => {
    System.err.println("*** shutting down gRPC server since JVM is shutting down")
    server.shutdownNow()
    System.err.println("*** server shut down")
  })

  println("started...")

  server.awaitTermination()

}
