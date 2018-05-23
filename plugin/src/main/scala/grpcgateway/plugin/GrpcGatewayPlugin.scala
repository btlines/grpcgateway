package grpcgateway.plugin

import sbt.AutoPlugin
import sbt.Keys._
import sbt._
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

object GrpcGatewayPlugin extends AutoPlugin {

  override def requires = ProtocPlugin

  override def projectSettings = Seq(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value,
      grpcgateway.generators.SwaggerGenerator -> (resourceManaged in Compile).value / "specs",
      grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value
    ),
    unmanagedResourceDirectories in Compile += resourceManaged.value / "main" / "specs",
    libraryDependencies ++= Seq(
      "beyondthelines" %% "grpcgatewayruntime" % grpcgateway.generators.BuildInfo.version
    ),
    resolvers += Resolver.bintrayRepo("beyondthelines", "maven")
  )

}
