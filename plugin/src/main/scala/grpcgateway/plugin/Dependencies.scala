package grpcgateway.plugin

import sbt._

import com.trueaccord.scalapb.compiler.Version.scalapbVersion

object Dependencies {

  lazy val grpc_netty = "io.grpc" % "grpc-netty" % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion
  lazy val scalapb_runtime = "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion
  lazy val scalapb_runtime_grpc = "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion
  lazy val googleapis_common_protos = "com.google.api.grpc" % "googleapis-common-protos" % "0.0.3"

  lazy val grpc_gateway_runtime = "beyondthelines" %% "grpcgatewayruntime" % grpcgateway.generators.BuildInfo.version

}
