import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion}

organization in ThisBuild := "beyondthelines"
version in ThisBuild := "0.0.10-SNAPSHOT"
licenses in ThisBuild := ("MIT", url("http://opensource.org/licenses/MIT")) :: Nil
bintrayOrganization in ThisBuild := Some("beyondthelines")
bintrayPackageLabels in ThisBuild := Seq("scala", "protobuf", "grpc")
scalaVersion in ThisBuild := "2.12.4"

lazy val runtime = (project in file("runtime"))
  .settings(
    crossScalaVersions := Seq("2.12.4", "2.11.11"),
    name := "GrpcGatewayRuntime",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb"   %% "compilerplugin"          % scalapbVersion,
      "com.thesamet.scalapb"   %% "scalapb-runtime-grpc"    % scalapbVersion,
      "com.thesamet.scalapb"   %% "scalapb-json4s"          % scalapbVersion,
      "io.grpc"                %  "grpc-netty"              % grpcJavaVersion,
      "org.webjars"            %  "swagger-ui"              % "3.5.0",
      "org.slf4j"              %  "slf4j-api"               % "1.7.25"
    )
  )

lazy val generator = (project in file("generator"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    crossScalaVersions := Seq("2.12.4", "2.10.6"),
    name := "GrpcGatewayGenerator",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb"   %% "compilerplugin"          % scalapbVersion,
      "com.thesamet.scalapb"   %% "scalapb-runtime-grpc"    % scalapbVersion,
      "com.google.api.grpc"    % "googleapis-common-protos" % "0.0.3" % "protobuf"
    ),
    buildInfoPackage := "grpcgateway.generators"
  )

lazy val plugin = (project in file("plugin"))
  .settings(
    sbtPlugin := true,
    name := "GrpcGatewayPlugin",
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.17")
  ).dependsOn(generator)