package testgen

import grpcgateway.test.{ConcatWords, SimpleServiceGrpc, SuccessResult}

import scala.concurrent.Future

class SimpleServiceImpl extends SimpleServiceGrpc.SimpleService {

  def concatWords(request: ConcatWords): Future[SuccessResult] = {
    val concat = request.word1.concat(request.word2)
    Future.successful(SuccessResult(concat))
  }

  override def concatWords2(request: ConcatWords): Future[SuccessResult] = {
    concatWords(request)
  }

}
