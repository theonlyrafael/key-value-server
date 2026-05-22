package ds.kvstore.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Holds active versions for a key, performing conflict resolution via vector clocks.
 */
public class StoreEntry {
    private final List<Version> active = new ArrayList<>();

    public synchronized List<Version> getActiveVersions() {
        return new ArrayList<>(active);
    }

    /**
     * Add a version, dropping dominated ones; if dominated by any, ignore.
     * If concurrent with all, keep multiple.
     */
    public synchronized void addVersion(Version v) {
        // If v is dominated by any, ignore
        for (Version cur : active) {
            VectorClock.Relation r = v.getVectorClock().compare(cur.getVectorClock());
            if (r == VectorClock.Relation.PRECEDES) {
                // v older than cur -> ignore
                return;
            }
            if (r == VectorClock.Relation.EQUAL) {
                // If equal, tie-breaker by timestamp: keep the newer, but it's rare; keep existing
                return;
            }
        }
        // Remove versions that v dominates
        Iterator<Version> it = active.iterator();
        while (it.hasNext()) {
            Version cur = it.next();
            VectorClock.Relation r = v.getVectorClock().compare(cur.getVectorClock());
            if (r == VectorClock.Relation.SUCCEEDS) {
                it.remove();
            }
        }
        active.add(v);
    }
}
