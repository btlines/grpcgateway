package grpc_server.service

import hellogrpc.calc.{CalcResponse, CalcServiceGrpc, SumRequest}

import scala.concurrent.Future

class SumServiceImpl extends CalcServiceGrpc.CalcService {
  def calcSum(request: SumRequest): Future[CalcResponse] = {
    Future.successful {
      CalcResponse(
        result = request.a + request.b
      )
    }
  }
}