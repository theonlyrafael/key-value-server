package integration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kvstore.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste opcional de conectividade com o servidor do professor.
 * - Defina a env var: PROF_SERVER_ADDR="host:port" (ex: 127.0.0.1:50051)
 * - O teste é automaticamente ignorado se a variável não estiver definida.
 */
public class ProfServerConnectivityTest {

    @Test
    void testPutGetAgainstProfessorServer() {
        String addr = System.getenv("PROF_SERVER_ADDR");
        Assumptions.assumeTrue(addr != null && addr.contains(":"),
                "Pulando teste: defina PROF_SERVER_ADDR (ex: 127.0.0.1:50051)");

        ManagedChannel ch = ManagedChannelBuilder.forTarget(addr).usePlaintext().build();
        KvStoreGrpc.KvStoreBlockingStub stub = KvStoreGrpc.newBlockingStub(ch);

        String key = "connectivity_test_key";
        String value = "hello_prof";

        PutResponse pr = stub.put(PutRequest.newBuilder().setKey(key).setValue(value).build());
        assertTrue(pr.getSuccess(), "PUT deve retornar success=true");

        GetResponse gr = stub.get(GetRequest.newBuilder().setKey(key).build());
        assertTrue(gr.getVersionsCount() >= 1, "GET deve retornar ao menos uma versão");
        boolean found = false;
        for (Version v : gr.getVersionsList()) {
            if (value.equals(v.getValue())) { found = true; break; }
        }
        assertTrue(found, "Alguma versão deve conter o valor que acabamos de gravar");
        ch.shutdownNow();
    }
}
