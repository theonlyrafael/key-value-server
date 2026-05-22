package ds.kvstore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VectorClockTest {

    @Test
    void testIncrementAndGet() {
        VectorClock vc = new VectorClock();
        assertEquals(0L, vc.get("A"));
        vc.increment("A");
        assertEquals(1L, vc.get("A"));
        vc.increment("A");
        assertEquals(2L, vc.get("A"));
    }

    @Test
    void testMerge() {
        VectorClock a = new VectorClock();
        a.increment("A"); // A:1

        VectorClock b = new VectorClock();
        b.increment("B"); // B:1

        a.merge(b);
        assertEquals(1L, a.get("A"));
        assertEquals(1L, a.get("B"));
    }

    @Test
    void testComparePrecedesSucceedsEqualConcurrent() {
        VectorClock x = new VectorClock();
        x.increment("A"); // A:1

        VectorClock y = new VectorClock();
        y.increment("A"); // A:1

        assertEquals(VectorClock.Relation.EQUAL, x.compare(y));

        // x < y
        y.increment("A"); // A:2
        assertEquals(VectorClock.Relation.PRECEDES, x.compare(y));
        assertEquals(VectorClock.Relation.SUCCEEDS, y.compare(x));

        // Concurrente: A:2 vs (A:1,B:1)
        VectorClock z = new VectorClock();
        z.increment("A");
        z.increment("B");
        assertEquals(VectorClock.Relation.CONCURRENT, y.compare(z));
        assertEquals(VectorClock.Relation.CONCURRENT, z.compare(y));
    }
}
