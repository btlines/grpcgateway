package grpcgateway.plugin

import sbt.AutoPlugin
import sbt.Keys._
import sbt._
import sbtprotoc.ProtocPlugin.autoImport.PB

object GrpcGatewayPlugin extends AutoPlugin {

  override def projectSettings = Seq(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value,
      // generate Swagger spec files into the resources/specs
      grpcgateway.generators.SwaggerGenerator -> (resourceDirectory in Compile).value / "specs",
      // generate the Rest Gateway source code
      grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value
    ),
    libraryDependencies ++= Seq(
      "beyondthelines" %% "grpcgatewayruntime" % grpcgateway.generators.BuildInfo.version % "compile,protobuf"
    )
  )
}
