package grpcgateway.plugin

import protocbridge.Target
import sbt.AutoPlugin
import sbt.Keys._
import sbt._
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB

import scala.collection.mutable.ListBuffer

object GrpcGatewayPlugin extends AutoPlugin {

  object autoImport {
    lazy val generateSwagger: SettingKey[Boolean] = SettingKey[Boolean]("Generate swagger specification")
  }

  import autoImport._

  override def requires = ProtocPlugin

  override def projectSettings = Seq(
    generateSwagger := true,
    PB.targets in Compile ++= {
      val res = ListBuffer.empty[Target]
      res += grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value
      if (generateSwagger.value) {
        res += grpcgateway.generators.SwaggerGenerator -> (resourceManaged in Compile).value / "specs"
      }
      res
    },
    libraryDependencies ++= Seq(Dependencies.grpc_gateway_runtime),
    resolvers += Resolver.bintrayRepo("beyondthelines", "maven")
  )

}
