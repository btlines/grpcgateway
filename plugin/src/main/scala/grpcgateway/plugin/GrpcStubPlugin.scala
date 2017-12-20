package grpcgateway.plugin

import com.trueaccord.scalapb.compiler.Version.scalapbVersion

import sbt._
import sbt.Keys._

object GrpcStubPlugin extends AutoPlugin {
  override def projectSettings = Seq(
    libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion
  )
}
