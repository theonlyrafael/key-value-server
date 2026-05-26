package ds.kvstore.grpc;

import ds.kvstore.server.ServerNode;
import ds.kvstore.model.Version;

import io.grpc.stub.StreamObserver;

// Classes geradas a partir do proto (src/main/proto/kvstore.proto)
import kvstore.KvStoreGrpc;
import kvstore.PutRequest;
import kvstore.PutResponse;
import kvstore.GetRequest;
import kvstore.GetResponse;
import kvstore.VectorClock;
import kvstore.VectorClockEntry;

/**
 * Implementação do serviço gRPC KvStore.
 * Usa o ServerNode para aplicar puts/gets e converte para as mensagens do proto.
 */
public class KvStoreServiceImpl extends KvStoreGrpc.KvStoreImplBase {
    private final ServerNode node;

    public KvStoreServiceImpl(ServerNode node) {
        this.node = node;
    }

    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        try {
            node.putFromClient(request.getKey(), request.getValue());
            responseObserver.onNext(
                PutResponse.newBuilder().setSuccess(true).build()
            );
        } catch (Exception e) {
            responseObserver.onNext(
                PutResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        GetResponse.Builder rb = GetResponse.newBuilder();

        // Itera sobre as versões do modelo interno e converte para proto
        for (Version v : node.get(request.getKey())) {
            VectorClock.Builder vcb = VectorClock.newBuilder();
            v.getVectorClock().toMap().forEach((nid, ctr) ->
                vcb.addEntries(
                    VectorClockEntry.newBuilder()
                            .setNodeId(nid)
                            .setCounter(ctr)
                            .build()
                )
            );

            kvstore.Version pv = kvstore.Version.newBuilder()
                    .setValue(v.getValue())
                    .setWriterNodeId(v.getWriterNodeId())
                    .setTimestamp(v.getTimestamp())
                    .setVectorClock(vcb.build())
                    .build();

            rb.addVersions(pv);
        }

        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
    }
}
