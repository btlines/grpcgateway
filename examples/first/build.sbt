scalaVersion in ThisBuild := "2.12.2"

organization in ThisBuild := "beyondthelines"

disablePlugins(RevolverPlugin)

lazy val protos = (project in file("protos"))
  .disablePlugins(RevolverPlugin)
  .settings(
    PB.targets in Compile += (scalapb.gen(flatPackage = false) -> (sourceManaged in Compile).value),
    libraryDependencies ++= Seq(
      grpcgateway.plugin.Dependencies.scalapb_runtime_grpc,
      grpcgateway.plugin.Dependencies.googleapis_common_protos % "protobuf",
      "beyondthelines" % "first_example_interface" % "0.0.1-SNAPSHOT" % "protobuf"
    ),
    PB.includePaths in Compile := Seq(target.value / "protobuf_external"),
    PB.protoSources in Compile := Seq(
      target.value / "protobuf_external" / "google" / "api" / "annotations.proto",
      target.value / "protobuf_external" / "google" / "api" / "http.proto",
      target.value / "protobuf_external" / "hellogrpc"
    )
  )

lazy val interface = (project in file("interface"))
  .disablePlugins(RevolverPlugin)
  .settings(
    name := "first_example_interface",
    version := "0.0.1-SNAPSHOT",
    crossPaths := false,
    autoScalaLibrary := false,
    unmanagedResourceDirectories in Compile ++= Seq(
      baseDirectory.value / "src" / "main" / "protobuf"
    )
  )

lazy val service = (project in file("service"))
  .settings(
    libraryDependencies ++= Seq(
      grpcgateway.plugin.Dependencies.grpc_netty
    )
  )
  .dependsOn(protos)

lazy val gateway = (project in file("gateway"))
  .enablePlugins(GrpcGatewayPlugin)
  .settings(
    fork in Test := true,
    PB.includePaths in Compile += (target in protos).value / "protobuf_external",
    PB.targets in Compile += (scalapb.gen(flatPackage = false) -> (sourceManaged in Compile).value),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "com.softwaremill.sttp" %% "core" % "1.1.2" % Test,
      "org.json4s" %% "json4s-jackson" % "3.5.3" % Test
    ),
    Revolver.enableDebugging(5015, suspend = true)
  ).dependsOn(protos)