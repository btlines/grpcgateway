organization in ThisBuild := "beyondthelines"
version in ThisBuild := "0.0.5"
licenses in ThisBuild := ("MIT", url("http://opensource.org/licenses/MIT")) :: Nil
bintrayOrganization in ThisBuild := Some("beyondthelines")
bintrayPackageLabels in ThisBuild := Seq("scala", "protobuf", "grpc")
scalaVersion in ThisBuild := "2.12.4"

lazy val runtime = (project in file("runtime"))
  .settings(
    crossScalaVersions := Seq("2.12.4", "2.11.11"),
    name := "GrpcGatewayRuntime",
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "compilerplugin"          % "0.6.7",
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"    % "0.6.7",
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"    % "0.6.7",
      "com.trueaccord.scalapb" %% "scalapb-json4s"          % "0.3.3",
      "io.grpc"                %  "grpc-netty"              % "1.8.0",
      "org.webjars"            %  "swagger-ui"              % "3.5.0",
      "com.google.api.grpc"    % "googleapis-common-protos" % "0.0.3" % "protobuf"
    ),
    PB.protoSources in Compile += target.value / "protobuf_external",
    includeFilter in PB.generate := new SimpleFilter(
      file => file.endsWith("annotations.proto") || file.endsWith("http.proto")
    ),
    PB.targets in Compile += scalapb.gen() -> (sourceManaged in Compile).value,
    mappings in (Compile, packageBin) ++= Seq(
      baseDirectory.value / "target" / "protobuf_external" / "google" / "api" / "annotations.proto" -> "google/api/annotations.proto",
      baseDirectory.value / "target" / "protobuf_external" / "google" / "api" / "http.proto"        -> "google/api/http.proto"
    )
  )

lazy val generator = (project in file("generator"))
  .settings(
    crossScalaVersions := Seq("2.12.4", "2.10.6"),
    name := "GrpcGatewayGenerator",
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "compilerplugin"          % "0.6.7",
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"    % "0.6.7",
      "com.google.api.grpc"    % "googleapis-common-protos" % "0.0.3" % "protobuf"
    ),
    PB.protoSources in Compile += target.value / "protobuf_external",
    includeFilter in PB.generate := new SimpleFilter(
      file => file.endsWith("annotations.proto") || file.endsWith("http.proto")
    ),
    PB.targets in Compile += PB.gens.java -> (sourceManaged in Compile).value
  )
