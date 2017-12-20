import grpcgateway.plugin.GrpcStubPlugin

scalaVersion in ThisBuild := "2.12.2"

organization in ThisBuild := "beyondthelines"

lazy val interface = (project in file("interface"))
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
  .enablePlugins(GrpcStubPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "beyondthelines" % "first_example_interface" % "0.0.1-SNAPSHOT" % "protobuf",
    ),
    PB.protoSources in Compile += target.value / "protobuf_external" / "hellogrpc",
    PB.targets in Compile += (scalapb.gen() -> (sourceManaged in Compile).value),
  )

lazy val gateway = (project in file("gateway"))
  .enablePlugins(GrpcGatewayPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "beyondthelines" % "first_example_interface" % "0.0.1-SNAPSHOT" % "protobuf",
    ),
    PB.targets in Compile += (scalapb.gen() -> (sourceManaged in Compile).value),
    PB.includePaths in Compile += target.value / "protobuf_external" / "hellogrpc"
  )