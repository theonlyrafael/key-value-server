package ds.kvstore.model;

public class Version {
    private final String value;
    private final long timestamp; // System.nanoTime() logical tiebreaker
    private final VectorClock vectorClock;
    private final String writerNodeId;

    public Version(String value, long timestamp, VectorClock vc, String writerNodeId) {
        this.value = value;
        this.timestamp = timestamp;
        this.vectorClock = vc;
        this.writerNodeId = writerNodeId;
    }

    public String getValue(){ return value; }
    public long getTimestamp(){ return timestamp; }
    public VectorClock getVectorClock(){ return vectorClock; }
    public String getWriterNodeId(){ return writerNodeId; }
}
