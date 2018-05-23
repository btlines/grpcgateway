Example project that demonstrates how to use grpc gateway generator

What we have here:

There are few modules.

1. interface:

Contains protobuf + grpc interfaces. This package is used in order to share these protobuf files between services.

2. protos:

Its purpose to aggregate all protofiles that we need in future and compile them. After that we can just depend on it and not to compile protobuf every time

3. service:

This is the service that implements our interface. It starts service on localhost:9095

4. gateway

This project has our gateway generator. It generates proxy handlers and swagger. We don't need to run scalapb generator because
we already have compiled protos in protos module.

For developers:

sbt reStart

Test:

there is small integration test in gateway module. run sbt gateway/test