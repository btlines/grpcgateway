lazy val commonSettings = Seq(
  organization := "beyondthelines",
  version := "0.0.2",
  licenses := ("MIT", url("http://opensource.org/licenses/MIT")) :: Nil,
  bintrayOrganization := Some("beyondthelines"),
  bintrayPackageLabels := Seq("scala", "protobuf", "grpc"),
    // set python location (only required for scalapb under windows)
  PB.pythonExe := "C:\\Python27\\Python.exe"
)

lazy val runtime = (project in file("runtime"))
  .settings(
    commonSettings,
    scalaVersion := "2.12.2",
    crossScalaVersions := Seq("2.1.2.2", "2.11.11"),
    name := "GrpcGatewayRuntime",
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "compilerplugin"          % "0.6.0-pre5",
      "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"    % "0.6.0-pre5",
      "com.trueaccord.scalapb" %% "scalapb-json4s"          % "0.3.0",
      "io.grpc"                %  "grpc-netty"              % "1.4.0",
      "org.webjars"            %  "swagger-ui"              % "3.1.5",
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
    commonSettings,
    scalaVersion := "2.10.6",
    name := "GrpcGatewayGenerator",
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %% "compilerplugin"          % "0.6.0-pre5",
      "com.google.api.grpc"    % "googleapis-common-protos" % "0.0.3" % "protobuf"
    ),
    PB.protoSources in Compile += target.value / "protobuf_external",
    includeFilter in PB.generate := new SimpleFilter(
      file => file.endsWith("annotations.proto") || file.endsWith("http.proto")
    ),
    PB.targets in Compile += PB.gens.java -> (sourceManaged in Compile).value
  )
