package io.stargate.it.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.stargate.it.BaseOsgiIntegrationTest;
import io.stargate.it.storage.StargateEnvironmentInfo;
import io.stargate.proto.QueryOuterClass.Query;
import io.stargate.proto.StargateGrpc;
import org.junit.jupiter.api.Test;

public class SimpleQuery extends BaseOsgiIntegrationTest {
  @Test
  public void simpleQueryTest(StargateEnvironmentInfo stargate) {
    // TODO: Consider reusing channel in multiple tests
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(stargate.nodes().get(0).seedAddress(), 8090)
            .usePlaintext()
            .build();
    StargateGrpc.StargateBlockingStub stub = StargateGrpc.newBlockingStub(channel);
    // TODO: Get and check result
    stub.execute(Query.newBuilder().setCql("SELECT * FROM system.local").build());
  }
}
