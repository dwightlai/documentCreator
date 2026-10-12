package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 使用按主题返回固定向量的模拟向量化服务，验证语义检索、混合排序与权限前置过滤。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeVectorSearchIntegrationTest {
    private static final String TENANT = "tenant-knowledge-vector";
    private static final HttpServer EMBEDDINGS = startEmbeddingServer();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void embeddingEndpoint(DynamicPropertyRegistry registry) {
        registry.add(
                "platform.knowledge.embedding.endpoint",
                () -> "http://127.0.0.1:" + EMBEDDINGS.getAddress().getPort() + "/v1/embeddings");
    }

    @AfterAll
    static void stop() {
        EMBEDDINGS.stop(0);
    }

    @Test
    void findsSemanticallyRelatedChunksWithinPermittedDocumentsOnly() throws Exception {
        String sourceId = data(mockMvc.perform(
                        post("/api/v1/knowledge/sources")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "admin")
                                .contentType("application/json")
                                .content(
                                        """
                        {"sourceCode":"HYBRID","sourceName":"民生政策库","category":"政策法规","storageType":"混合检索",
                         "permissionScope":"本单位","vectorModel":"stub-embedding","vectorDimensions":4}
                        """)))
                .path("id")
                .asText();
        String elderly = upload(sourceId, "养老服务实施办法", "内部", "推进养老机构建设，完善社区居家养老服务网络。");
        upload(sourceId, "道路交通管理规定", "内部", "加强道路交通安全管理，优化公共交通线路。");
        upload(sourceId, "养老专项资金安排（机密）", "机密", "养老机构补助资金的具体安排。");

        // 查询词与文档没有共同的关键词，全文检索找不到
        JsonNode keyword = data(search("老年人照护", "keyword", null));
        assertThat(keyword).isEmpty();

        // 语义检索命中养老文档；机密文档对普通用户不可见（权限前置过滤）
        JsonNode semantic = data(search("老年人照护", "hybrid", "platform.user"));
        List<String> titles = new ArrayList<>();
        semantic.forEach(hit -> titles.add(hit.path("documentTitle").asText()));
        assertThat(titles).first().isEqualTo("养老服务实施办法");
        assertThat(titles).doesNotContain("养老专项资金安排（机密）");
        // 与查询无关的交通文档低于相似度阈值，不进入结果
        assertThat(titles).containsExactly("养老服务实施办法");
        assertThat(semantic.get(0).path("matchType").asText()).isEqualTo("语义");

        // 关键词与语义同时命中时标记为混合，并排在最前
        JsonNode hybrid = data(search("养老机构", "hybrid", "platform.user"));
        assertThat(hybrid.get(0).path("documentId").asText()).isEqualTo(elderly);
        assertThat(hybrid.get(0).path("matchType").asText()).isEqualTo("混合");
        assertThat(hybrid.get(0).path("score").asDouble()).isEqualTo(1.0);
    }

    private String upload(String sourceId, String title, String permission, String text) throws Exception {
        String documentId = data(mockMvc.perform(multipart("/api/v1/knowledge/sources/{id}/documents/upload", sourceId)
                        .file(new MockMultipartFile(
                                "file", title + ".txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)))
                        .param("title", title)
                        .param("authorityLevel", "政策法规")
                        .param("permissionLevel", permission)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "admin")))
                .path("id")
                .asText();
        JsonNode parsed =
                data(mockMvc.perform(post("/api/v1/knowledge/sources/{s}/documents/{d}/parse", sourceId, documentId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "admin")));
        assertThat(parsed.path("warnings")).isEmpty();
        return documentId;
    }

    private ResultActions search(String query, String mode, String role) throws Exception {
        var request = get("/api/v1/knowledge/search").param("q", query).param("mode", mode);
        if (role == null) return mockMvc.perform(request.header("X-Tenant-Id", TENANT));
        return mockMvc.perform(
                request.with(jwt().jwt(token -> token.subject("reader").claim("tenant_id", TENANT))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    /** 养老/老年 → 主题一，交通/道路 → 主题二，其他 → 主题三。 */
    private static HttpServer startEmbeddingServer() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                JsonNode input = mapper.readTree(exchange.getRequestBody()).path("input");
                StringBuilder data = new StringBuilder("[");
                for (int i = 0; i < input.size(); i++) {
                    String text = input.get(i).asText();
                    String vector = text.contains("养老") || text.contains("老年")
                            ? "[1,0.1,0,0]"
                            : text.contains("交通") || text.contains("道路") ? "[0,1,0.1,0]" : "[0,0,0.1,1]";
                    if (i > 0) data.append(',');
                    data.append("{\"index\":")
                            .append(i)
                            .append(",\"embedding\":")
                            .append(vector)
                            .append('}');
                }
                byte[] response = ("{\"data\":" + data + "]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
