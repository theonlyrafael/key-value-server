package ds.kvstore.server;

import ds.kvstore.grpc.KvStoreServiceImpl;
import ds.kvstore.replication.MqttReplicationClient;
import ds.kvstore.replication.Replicator;
import io.grpc.Server;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class GrpcServer {
    private static final Logger LOG = Logger.getLogger(GrpcServer.class.getName());

    public static void main(String[] args) throws Exception {
        String nodeId = null;
        String listenAddr = "127.0.0.1:50051";
        String brokerAddr = "127.0.0.1";
        int brokerPort = 1883;

        // Parse flags
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--node-id":
                    nodeId = args[++i];
                    break;
                case "--listen-addr":
                    listenAddr = args[++i];
                    break;
                case "--mqtt-broker-addr":
                    brokerAddr = args[++i];
                    break;
                case "--mqtt-broker-port":
                    brokerPort = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Arg desconhecido: " + args[i]);
            }
        }
        if (nodeId == null) nodeId = java.util.UUID.randomUUID().toString();

        // Bind gRPC em IP:porta
        String[] hp = listenAddr.split(":");
        InetSocketAddress bind = new InetSocketAddress(hp[0], Integer.parseInt(hp[1]));

        // Estado e nó
        NodeState state = new NodeState(nodeId);
        ServerNode serverNode = new ServerNode(state, null);

        // MQTT (replicação): conecta, assina e prepara publisher
        String brokerUrl = String.format("tcp://%s:%d", brokerAddr, brokerPort);
        Replicator repl = new MqttReplicationClient(serverNode, brokerUrl);
        serverNode.setReplicator(repl);

        // Servidor gRPC
        Server server = NettyServerBuilder.forAddress(bind)
                .addService(new KvStoreServiceImpl(serverNode))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        // >>> Sem lambda (evita "effectively final")
        LOG.info(String.format("Node=%s gRPC=%s MQTT=%s", nodeId, listenAddr, brokerUrl));

        server.awaitTermination();
    }
}
