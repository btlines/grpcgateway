package gateway

import scala.concurrent.ExecutionContext

object Main extends App {

  implicit val ec = ExecutionContext.Implicits.global

  val server = GatewayServer(
    channelAddress = "localhost",
    channelPort = 9095,
    gwPort = 9097
  )

  server.gateway.start()

  sys.addShutdownHook {
    println("shutting down gateway")
  }

  println("gateway started")

}
