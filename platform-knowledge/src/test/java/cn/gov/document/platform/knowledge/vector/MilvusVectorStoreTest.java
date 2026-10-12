package cn.gov.document.platform.knowledge.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.document.platform.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 以模拟的 Milvus REST v2 服务验证请求格式、权限过滤表达式和结果解析。 */
class MilvusVectorStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> requests = new ConcurrentHashMap<>();
    private HttpServer server;
    private MilvusVectorStore store;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.put(path, mapper.readTree(exchange.getRequestBody()));
            String body =
                    switch (path) {
                        case "/v2/vectordb/collections/has" -> "{\"code\":0,\"data\":{\"has\":false}}";
                        case "/v2/vectordb/entities/search" -> "{\"code\":0,\"data\":[{\"id\":\"c1\",\"distance\":0.92},{\"id\":\"c2\",\"distance\":0.41}]}";
                        default -> "{\"code\":0,\"data\":{}}";
                    };
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        store = new MilvusVectorStore(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", "chunks", 3, "secret", mapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void createsCollectionUpsertsAndSearchesWithTenantAndDocumentFilter() {
        store.upsert("tenant1", List.of(new VectorStore.Record("c1", "doc1", new float[] {0.1f, 0.2f, 0.3f})));
        JsonNode create = requests.get("/v2/vectordb/collections/create");
        assertThat(create.path("dimension").asInt()).isEqualTo(3);
        assertThat(create.path("metricType").asText()).isEqualTo("COSINE");
        JsonNode row = requests.get("/v2/vectordb/entities/upsert").path("data").get(0);
        assertThat(row.path("tenant_id").asText()).isEqualTo("tenant1");
        assertThat(row.path("document_id").asText()).isEqualTo("doc1");

        var hits = store.search("tenant1", new float[] {0.1f, 0.2f, 0.3f}, List.of("doc1", "doc2"), 5);
        assertThat(hits).extracting(VectorStore.Hit::chunkId).containsExactly("c1", "c2");
        assertThat(requests.get("/v2/vectordb/entities/search").path("filter").asText())
                .isEqualTo("tenant_id == \"tenant1\" and document_id in [\"doc1\",\"doc2\"]");
    }

    @Test
    void rejectsIdentifiersThatCouldInjectIntoFilterExpressions() {
        assertThatThrownBy(() -> store.search("tenant1", new float[] {1, 0, 0}, List.of("doc1\" or 1 == 1"), 5))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () -> store.upsert("tenant1", List.of(new VectorStore.Record("c1", "doc1", new float[] {1}))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度");
    }
}
