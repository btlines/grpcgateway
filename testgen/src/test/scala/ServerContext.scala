import grpcgateway.server.{GrpcGatewayServer, GrpcGatewayServerBuilder}
import grpcgateway.test.{SimpleServiceGrpc, SimpleServiceHandler}
import io.grpc.{ManagedChannel, Server, ServerBuilder}
import testgen.SimpleServiceImpl

import scala.concurrent.ExecutionContext.Implicits.global

class ServerContext(
  serverPort: Int,
  gatewayPort: Int
) {

  val ec = global

  private var server: Server = _
  private var gateway: GrpcGatewayServer = _
  private var channel: ManagedChannel = _

  private def startServer(port: Int): Unit = {

    server = ServerBuilder
      .forPort(port)
      .addService(SimpleServiceGrpc.bindService(new SimpleServiceImpl(), ec))
      .build().start()

    sys.addShutdownHook(() => {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      server.shutdownNow()
      System.err.println("*** server shut down")
    })

    println("server started...")

  }

  private def startChannel(address: String, port: Int): Unit = {

    channel =
      io.grpc.ManagedChannelBuilder
        .forAddress(address, port)
        .usePlaintext(true)
        .build()

    println("channel created")

  }

  private def startGateway(port: Int): Unit = {

    gateway = GrpcGatewayServerBuilder
      .forPort(port)
      .addService(new SimpleServiceHandler(channel))
      .build()

    gateway.start()

    println("gateway started")

  }

  def start(): Unit = {
    if (server == null) {
      startServer(serverPort)
      startChannel("localhost", serverPort)
      startGateway(gatewayPort)
    }
  }

  def shutdown(): Unit = {
    if (server != null) {
      gateway.shutdown(); gateway = null
      server.shutdown(); server = null
      channel.shutdown(); channel = null
    }
  }



}
