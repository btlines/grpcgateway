[![Build status](https://api.travis-ci.org/btlines/grpcgateway.svg?branch=master)](https://travis-ci.org/btlines/grpcgateway)
[![Dependencies](https://app.updateimpact.com/badge/852442212779298816/grpcgateway.svg?config=compile)](https://app.updateimpact.com/latest/852442212779298816/grpcgateway)
[![License](https://img.shields.io/:license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![GRPCGatewayGenerator](https://api.bintray.com/packages/beyondthelines/maven/grpcgatewaygenerator/images/download.svg) ](https://bintray.com/beyondthelines/maven/grpcgatewaygenerator/_latestVersion)
[![GRPCGatewayRuntime](https://api.bintray.com/packages/beyondthelines/maven/grpcgatewayruntime/images/download.svg) ](https://bintray.com/beyondthelines/maven/grpcgatewayruntime/_latestVersion)

# GRPC Gateway

Automatically derive REST services from GRPC specifications.

## Principle

GRPCGateway is inspired by the [GRPCGateway originally developed in Go](https://github.com/grpc-ecosystem/grpc-gateway). 
It creates a Proxy forwarding REST calls to GRPC services. 

## Installation

You need to enable [`sbt-protoc`](https://github.com/thesamet/sbt-protoc) plugin to generate source code for the proto definitions.
You can do it by adding a `protoc.sbt` file into your `project` folder with the following lines:

```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.9")

resolvers += Resolver.bintrayRepo("beyondthelines", "maven")

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7",
  "beyondthelines"         %% "grpcgatewaygenerator" % "0.0.5"
)
```

Here we add a dependency to the protobuf generator for the gRPC gateway in order to be able to generate REST services and Swagger documentation.

Then we need to trigger the generation from the `build.sbt`:

```scala
PB.targets in Compile := Seq(
  // compile your proto files into scala source files
  scalapb.gen() -> (sourceManaged in Compile).value,
  // generate Swagger spec files into the `resources/specs`
  grpcgateway.generators.SwaggerGenerator -> (resourceDirectory in Compile).value / "specs",
  // generate the Rest Gateway source code
  grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value
)

resolvers += Resolver.bintrayRepo("beyondthelines", "maven")

libraryDependencies += "beyondthelines" %% "grpcgatewayruntime" % "0.0.5" % "compile,protobuf"
```

### Usage

You're now ready to create your GRPC gateway.

First thing is to annotate the proto files to define the REST endpoints:

```
import "google/api/annotations.proto";

rpc GetFeature(Point) returns (Feature) {
  option (google.api.http) = {
    get: "/v1/example/feature"
  };
}
```

Then you need to create a GRPC channel pointing to your GRPC server:

```scala
val channel = 
  io.grpc.ManagedChannelBuilder
    .forAddress("localhost", 8980)
    .usePlaintext(true)
    .build()
```

and finally create the gateway itself:

```scala
val gateway = GrpcGatewayServerBuilder
    .forPort(8981)
    .addService(new RouteGuideHandler(channel))
    .build()
    
```

Here `RouteGuideHandler` is the handler automatically generated from the protobuf files.

You can use `start()` and `shutdown()` to start and shutdown the gateway.

```scala
gateway.start()

sys.addShutdownHook {
  gateway.shutdown()
}
```

### API Documentation

A swagger spec for the supported services is automatically generated and served using swagger-ui.

Once the gateway is running you can point your browser to [http://localhost:8981/docs/index.html?url=/specs/RouteGuide.yml](http://localhost:8981/docs/index.html?url=/specs/RouteGuide.yml)

where `RouteGuide.yml` is the Swagger spec automatically generated from the protobuf files.


## Limitations

Only GRPC Unary calls are supported (i.e. No streaming supported).
