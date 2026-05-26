package ds.kvstore.replication;

import ds.kvstore.model.Version;

public interface Replicator {
    void publishUpdate(String key);
    default void onLocalPut(String key, Version v) { publishUpdate(key); }
}
