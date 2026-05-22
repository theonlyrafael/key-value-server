package ds.kvstore.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Vector Clock with happens-before comparison.
 */
public class VectorClock {
    private final Map<String, Long> clock = new HashMap<>();

    public VectorClock() {}

    public VectorClock(Map<String, Long> map) {
        if (map != null) clock.putAll(map);
    }

    public synchronized void increment(String nodeId) {
        clock.put(nodeId, get(nodeId) + 1L);
    }

    public synchronized long get(String nodeId) {
        return clock.getOrDefault(nodeId, 0L);
    }

    public synchronized Map<String, Long> toMap() {
        return Collections.unmodifiableMap(new HashMap<>(clock));
    }

    public synchronized void merge(VectorClock other) {
        if (other == null) return;
        for (var e : other.clock.entrySet()) {
            clock.put(e.getKey(), Math.max(clock.getOrDefault(e.getKey(), 0L), e.getValue()));
        }
    }

    public enum Relation { PRECEDES, SUCCEEDS, CONCURRENT, EQUAL }

    /**
     * Compare this VC to other:
     * - PRECEDES if this <= other and strictly less for at least one entry;
     * - SUCCEEDS if this >= other and strictly greater for at least one entry;
     * - EQUAL if equal for all entries;
     * - CONCURRENT otherwise.
     */
    public synchronized Relation compare(VectorClock other) {
        if (other == null) return Relation.CONCURRENT;
        boolean less = false, greater = false;
        Set<String> keys = new java.util.HashSet<>(clock.keySet());
        keys.addAll(other.clock.keySet());
        for (String k : keys) {
            long a = clock.getOrDefault(k, 0L);
            long b = other.clock.getOrDefault(k, 0L);
            if (a < b) less = true;
            if (a > b) greater = true;
        }
        if (!less && !greater) return Relation.EQUAL;
        if (less && !greater) return Relation.PRECEDES;
        if (!less && greater) return Relation.SUCCEEDS;
        return Relation.CONCURRENT;
    }

    @Override public synchronized String toString(){ return clock.toString(); }
    @Override public synchronized int hashCode(){ return Objects.hash(clock); }
    @Override public synchronized boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof VectorClock vc)) return false;
        return Objects.equals(clock, vc.clock);
    }
}
