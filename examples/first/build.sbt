import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion}

scalaVersion in ThisBuild := "2.12.4"

organization in ThisBuild := "beyondthelines"

resolvers in ThisBuild += Resolver.bintrayRepo("beyondthelines", "maven")

disablePlugins(RevolverPlugin)

lazy val protos = (project in file("protos"))
  .disablePlugins(RevolverPlugin)
  .settings(
    PB.targets in Compile += (scalapb.gen() -> (sourceManaged in Compile).value),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
      "com.google.api.grpc" % "googleapis-common-protos" % "0.0.3" % "protobuf",
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
      "io.grpc" % "grpc-netty" % grpcJavaVersion
    )
  )
  .dependsOn(protos)

lazy val gateway = (project in file("gateway"))
  .enablePlugins(GrpcGatewayPlugin)
  .settings(
    fork in Test := true,
    PB.includePaths in Compile += (target in protos).value / "protobuf_external",
    PB.targets in Compile := Seq(
      grpcgateway.generators.SwaggerGenerator -> (resourceManaged in Compile).value / "specs",
      grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value
    ),
    unmanagedResourceDirectories in Compile += resourceManaged.value / "main" / "specs",
    libraryDependencies ++= Seq(
      "beyondthelines" % "first_example_interface" % "0.0.1-SNAPSHOT" % "protobuf",
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "com.softwaremill.sttp" %% "core" % "1.1.2" % Test,
      "org.json4s" %% "json4s-jackson" % "3.5.3" % Test
    ),
//    Revolver.enableDebugging(5015, suspend = true),
    PB.protoSources in Compile := Seq(
      target.value / "protobuf_external" / "hellogrpc"
    )
  ).dependsOn(protos)