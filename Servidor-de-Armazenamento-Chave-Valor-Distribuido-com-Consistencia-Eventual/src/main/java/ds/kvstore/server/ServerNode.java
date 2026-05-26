package ds.kvstore.server;

import ds.kvstore.model.Version;
import ds.kvstore.replication.Replicator;

import java.util.List;

public class ServerNode {
    private final NodeState state;
    private Replicator replicator; // set after constructed if needed

    public ServerNode(NodeState state, Replicator replicator) {
        this.state = state;
        this.replicator = replicator;
    }

    public void setReplicator(Replicator r){ this.replicator = r; }
    public NodeState getState(){ return state; }

    // Called by gRPC (client-originated)
    public void putFromClient(String key, String value) {
        Version v = state.processPut(key, value);
        if (replicator != null) {
            replicator.onLocalPut(key, v); // publish StoreEntry for key
        }
    }

    public void applyRemote(String key, Version v) {
        state.applyRemote(key, v);
    }

    public List<Version> get(String key) {
        return state.getActive(key);
    }
}
