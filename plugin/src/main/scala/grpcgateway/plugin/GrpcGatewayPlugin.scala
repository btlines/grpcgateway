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
    lazy val includeGoogleProtoAnnotatios: SettingKey[Boolean] = SettingKey[Boolean]("Include googleapis-common-protos to dependency list")
  }

  import autoImport._

  override def requires = ProtocPlugin

  override def projectSettings = Seq(
    includeGoogleProtoAnnotatios := true,
    generateSwagger := true,
    PB.targets in Compile ++= {
      val res = ListBuffer.empty[Target]
      res += grpcgateway.generators.SwaggerGenerator -> (resourceManaged in Compile).value / "specs"
      if (generateSwagger.value) {
        res += grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value
      }
      res
    },
    libraryDependencies ++= {
      val res = ListBuffer.empty[ModuleID]
      res += "beyondthelines" %% "grpcgatewayruntime" % grpcgateway.generators.BuildInfo.version
      if (includeGoogleProtoAnnotatios.value) {
        res += "com.google.api.grpc" % "googleapis-common-protos" % "0.0.3" % "protobuf"
      }
      res
    },
    resolvers += Resolver.bintrayRepo("beyondthelines", "maven")
  )

}
