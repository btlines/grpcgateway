package grpc_server.service

import hellogrpc.calc.CalcService.{ CalcResponse, CalcServiceGrpc, SumRequest }

import scala.concurrent.Future

class SumServiceImpl extends CalcServiceGrpc.CalcService {
  def calcSum(request: SumRequest): Future[CalcResponse] = {
    Future.failed(new Exception("Not impl"))
  }
}