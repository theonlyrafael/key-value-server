package ds.kvstore.replication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import ds.kvstore.model.VectorClock;
import ds.kvstore.model.Version;
import ds.kvstore.server.ServerNode;
import org.eclipse.paho.client.mqttv3.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MqttReplicationClient implements Replicator, MqttCallback {
    private static final Logger LOG = Logger.getLogger(MqttReplicationClient.class.getName());
    public static final String TOPIC = "kvstore/replication";

    private final ServerNode node;
    private final MqttClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public MqttReplicationClient(ServerNode node, String brokerUrl) throws MqttException {
        this.node = node;
        String cid = "kv-" + node.getState().getNodeId() + "-" + UUID.randomUUID();
        this.client = new MqttClient(brokerUrl, cid);
        this.client.setCallback(this);
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        this.client.connect(opts);
        this.client.subscribe(TOPIC, 1);
        LOG.info(() -> "MQTT connected to " + brokerUrl + " as " + cid);
    }

    // Publish StoreEntry for a given key (all active versions) with QoS 1
    @Override
    public void publishUpdate(String key) {
        try {
            var active = node.get(key);
            StoreEntryMsg se = new StoreEntryMsg();
            se.key = key;
            se.versions = new ArrayList<>();
            for (Version ver : active) {
                VersionMsg vm = new VersionMsg();
                vm.value = ver.getValue();
                vm.timestamp = ver.getTimestamp();
                vm.writerNodeId = ver.getWriterNodeId();
                vm.vectorClock = ver.getVectorClock().toMap();
                se.versions.add(vm);
            }
            String json = mapper.writeValueAsString(se);
            MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            client.publish(TOPIC, msg);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to publish replication", e);
        }
    }

    // MqttCallback
    @Override public void connectionLost(Throwable cause) {
        LOG.log(Level.WARNING, "MQTT connection lost", cause);
    }
    @Override public void deliveryComplete(IMqttDeliveryToken token) {}
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if (!TOPIC.equals(topic)) return;
        try {
            String json = new String(message.getPayload(), StandardCharsets.UTF_8);
            StoreEntryMsg se = mapper.readValue(json, StoreEntryMsg.class);
            if (se == null || se.key == null || se.versions == null) return;
            for (VersionMsg vm : se.versions) {
                VectorClock vc = new VectorClock(vm.vectorClock);
                ds.kvstore.model.Version v = new ds.kvstore.model.Version(
                    vm.value, vm.timestamp, vc, vm.writerNodeId
                );
                node.applyRemote(se.key, v);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to process MQTT message", e);
        }
    }

    // DTOs for JSON
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoreEntryMsg {
        public String key;
        public List<VersionMsg> versions;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
        public static class VersionMsg {
        public String value;
        public long timestamp;
        public String writerNodeId;
        public Map<String, Long> vectorClock;  // <---- use Long aqui
    }
}
