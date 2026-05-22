package ds.kvstore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class StoreEntryTest {

    @Test
    void testDominatedVersionIsDropped() {
        StoreEntry entry = new StoreEntry();

        // v1: A:1
        VectorClock vc1 = new VectorClock();
        vc1.increment("A");
        Version v1 = new Version("v1", 1L, vc1, "A");
        entry.addVersion(v1);

        // v2: A:2 (sucede v1) -> deve substituir
        VectorClock vc2 = new VectorClock();
        vc2.increment("A"); vc2.increment("A");
        Version v2 = new Version("v2", 2L, vc2, "A");
        entry.addVersion(v2);

        List<Version> actives = entry.getActiveVersions();
        assertEquals(1, actives.size());
        assertEquals("v2", actives.get(0).getValue());
    }

    @Test
    void testConcurrentVersionsAreKept() {
        StoreEntry entry = new StoreEntry();

        // vA: A:1
        VectorClock vca = new VectorClock();
        vca.increment("A");
        Version vA = new Version("A", 1L, vca, "node_A");
        entry.addVersion(vA);

        // vB: B:1 (concorrente com vA)
        VectorClock vcb = new VectorClock();
        vcb.increment("B");
        Version vB = new Version("B", 1L, vcb, "node_B");
        entry.addVersion(vB);

        var actives = entry.getActiveVersions();
        assertEquals(2, actives.size(), "Versões concorrentes devem permanecer ativas");
    }
}
