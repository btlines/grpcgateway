scalaVersion in ThisBuild := "2.12.2"

organization in ThisBuild := "beyondthelines"

resolvers in ThisBuild += Resolver.bintrayRepo("beyondthelines", "maven")

disablePlugins(RevolverPlugin)

lazy val protos = (project in file("protos"))
  .disablePlugins(RevolverPlugin)
  .settings(
    PB.targets in Compile += (scalapb.gen() -> (sourceManaged in Compile).value),
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
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
      "io.grpc" % "grpc-netty" % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion
    )
  )
  .dependsOn(protos)

lazy val gateway = (project in file("gateway"))
  .enablePlugins(GrpcGatewayPlugin)
  .settings(
    fork in Test := true,
    PB.includePaths in Compile += (target in protos).value / "protobuf_external",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "com.softwaremill.sttp" %% "core" % "1.1.2" % Test,
      "org.json4s" %% "json4s-jackson" % "3.5.3" % Test
    ),
    Revolver.enableDebugging(5015, suspend = true)
  ).dependsOn(protos)