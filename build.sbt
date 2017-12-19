organization in ThisBuild := "beyondthelines"
version in ThisBuild := "0.0.7-SNAPSHOT"
licenses in ThisBuild := ("MIT", url("http://opensource.org/licenses/MIT")) :: Nil
bintrayOrganization in ThisBuild := Some("beyondthelines")
bintrayPackageLabels in ThisBuild := Seq("scala", "protobuf", "grpc")
scalaVersion in ThisBuild := "2.12.4"

lazy val runtime = (project in file("runtime"))
  .settings(
    crossScalaVersions := Seq("2.12.4", "2.11.11"),
    name := "GrpcGatewayRuntime",
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"    % "0.6.7",
      "com.trueaccord.scalapb" %% "scalapb-json4s"          % "0.3.3",
      "io.grpc"                %  "grpc-netty"              % "1.8.0",
      "org.webjars"            %  "swagger-ui"              % "3.5.0",
    )
  )

lazy val generator = (project in file("generator"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoPackage := "grpcgateway.generators",
    crossScalaVersions := Seq("2.12.4", "2.10.6"),
    name := "GrpcGatewayGenerator",
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "compilerplugin"          % "0.6.7",
      "com.google.api.grpc"    % "googleapis-common-protos" % "0.0.3"
    )
  )

lazy val plugin = (project in file("plugin"))
  .settings(
    sbtPlugin := true,
    name := "GrpcGatewayPlugin",
    libraryDependencies ++= Seq(
      "beyondthelines" %% "grpcgatewaygenerator" % "0.0.6"
    ),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.13")
  )
