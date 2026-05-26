package testsupport;

import ds.kvstore.replication.Replicator;

public class NoopReplicator implements Replicator {
    @Override
    public void publishUpdate(String key) { /* no-op */ }
}
