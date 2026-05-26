package ds.kvstore.grpc;

import ds.kvstore.server.NodeState;
import ds.kvstore.server.ServerNode;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import kvstore.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import testsupport.NoopReplicator;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class KvStoreGrpcTest {
    private static Server server;
    private static ManagedChannel channel;
    private static KvStoreGrpc.KvStoreBlockingStub stub;

    @BeforeAll
    static void startServer() throws IOException {
        String addr = "127.0.0.1";
        int port = 50551;
        NodeState state = new NodeState("node_test");
        ServerNode node = new ServerNode(state, new NoopReplicator());

        server = NettyServerBuilder
                .forAddress(new InetSocketAddress(addr, port))
                .addService(new KvStoreServiceImpl(node))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        channel = ManagedChannelBuilder.forAddress(addr, port).usePlaintext().build();
        stub = KvStoreGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void stopServer() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Test
    void testPutAndGet() {
        PutRequest req = PutRequest.newBuilder().setKey("k").setValue("v").build();
        PutResponse pr = stub.put(req);
        assertTrue(pr.getSuccess());

        GetResponse gr = stub.get(GetRequest.newBuilder().setKey("k").build());
        assertTrue(gr.getVersionsCount() >= 1);
        Version v = gr.getVersions(0);
        assertEquals("v", v.getValue());
        assertEquals("node_test", v.getWriterNodeId());
    }
}
