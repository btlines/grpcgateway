package grpc_server.service

import hellogrpc.greet.GreetService.{ GreetRequest, GreetResponse, GreetServiceGrpc }

import scala.concurrent.Future

class GreetServiceImpl extends GreetServiceGrpc.GreetService {
  def greet(request: GreetRequest): Future[GreetResponse] = {
    Future.successful(GreetResponse(s"Hello ${request.name}"))
  }
}
