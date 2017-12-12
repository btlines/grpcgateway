package grpcgateway.handlers

import io.grpc.Status.Code

sealed trait GatewayException extends Exception {
  def code: Int
  def details: String
}

case class InvalidArgument(details: String)
  extends Exception(details)
  with GatewayException {
  val code: Int = Code.INVALID_ARGUMENT.value()
}
