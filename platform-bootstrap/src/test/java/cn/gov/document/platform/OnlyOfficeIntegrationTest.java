package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 定稿稿在线编辑：签名的编辑器配置、文档服务器凭访问令牌拉取文件、带签名的保存回调形成新定稿稿，以及各类伪造回调被拒绝。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OnlyOfficeIntegrationTest {
    private static final String TENANT = "tenant-onlyoffice";
    private static final String SECRET = "onlyoffice-test-secret-0123456789abcdef";
    private static final HttpServer DOCUMENT_SERVER = startDocumentServer();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void onlyOffice(DynamicPropertyRegistry registry) {
        registry.add("platform.onlyoffice.enabled", () -> "true");
        registry.add("platform.onlyoffice.public-url", () -> "https://platform.example.gov.cn/office");
        registry.add(
                "platform.onlyoffice.internal-url",
                () -> "http://127.0.0.1:" + DOCUMENT_SERVER.getAddress().getPort());
        registry.add("platform.onlyoffice.callback-base-url", () -> "http://backend:18089");
        registry.add("platform.onlyoffice.jwt-secret", () -> SECRET);
    }

    @AfterAll
    static void stop() {
        DOCUMENT_SERVER.stop(0);
    }

    @Test
    void editsTheFinalDraftThroughSignedConfigFileAccessAndCallback() throws Exception {
        String taskId = createTaskWithOneParagraph();
        JsonNode typeset = data(mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/typesets", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":{\"doc_number\":\"示政〔2026〕18号\",\"signer\":\"张某\"}}")));
        String typesetId = typeset.path("id").asText();

        mockMvc.perform(get("/api/v1/onlyoffice/status").header("X-Tenant-Id", TENANT))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.documentServerUrl").value("https://platform.example.gov.cn/office"));
        JsonNode editor = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{taskId}/document/typesets/{id}/editor-config", taskId, typesetId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice")));
        JsonNode config = editor.path("config");
        assertThat(config.path("document").path("key").asText()).isEqualTo(typesetId);
        assertThat(config.path("editorConfig").path("user").path("id").asText()).isEqualTo("alice");
        SignedJWT configToken = SignedJWT.parse(config.path("token").asText());
        assertThat(configToken.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8))))
                .isTrue();
        assertThat(configToken.getJWTClaimsSet().getStringClaim("documentType")).isEqualTo("word");

        // 文档服务器不带登录身份，凭访问令牌拉取文件；令牌中的租户生效
        String filePath = path(config.path("document").path("url").asText());
        byte[] original = mockMvc.perform(get(filePath))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(original).hasSizeGreaterThan(1000);
        String callbackPath =
                path(config.path("editorConfig").path("callbackUrl").asText());
        String editedUrl = "https://platform.example.gov.cn/office/cache/files/edited.docx";

        // 伪造或不合规的回调：未签名、签名错误、文件不在文档服务器、用文件令牌回调
        callback(callbackPath, Map.of("key", typesetId, "status", 2, "url", editedUrl), null)
                .andExpect(jsonPath("$.error").value(1));
        callback(
                        callbackPath,
                        Map.of("key", typesetId, "status", 2, "url", editedUrl),
                        "wrong-secret-0123456789abcdef-0123456789")
                .andExpect(jsonPath("$.error").value(1));
        callback(callbackPath, Map.of("key", typesetId, "status", 2, "url", "http://attacker.example/x.docx"), SECRET)
                .andExpect(jsonPath("$.error").value(1));
        String fileToken = filePath.substring(filePath.lastIndexOf('/') + 1);
        callback(
                        "/api/v1/onlyoffice/callback/" + fileToken,
                        Map.of("key", typesetId, "status", 2, "url", editedUrl),
                        SECRET)
                .andExpect(jsonPath("$.error").value(1));
        callback(callbackPath, Map.of("key", "another-document", "status", 2, "url", editedUrl), SECRET)
                .andExpect(jsonPath("$.error").value(1));
        // 编辑中（状态 1）无需保存
        callback(callbackPath, Map.of("key", typesetId, "status", 1), SECRET)
                .andExpect(jsonPath("$.error").value(0));
        assertThat(latest(taskId).path("id").asText()).isEqualTo(typesetId);

        // 编辑完成：从文档服务器（浏览器地址改写为内部地址）下载并追加为新的定稿稿；重复回调同一文件不重复记录
        callback(callbackPath, Map.of("key", typesetId, "status", 2, "url", editedUrl), SECRET)
                .andExpect(jsonPath("$.error").value(0));
        JsonNode edited = latest(taskId);
        assertThat(edited.path("id").asText()).isNotEqualTo(typesetId);
        assertThat(edited.path("source").asText()).isEqualTo("在线编辑");
        assertThat(edited.path("fieldValues").path("doc_number").asText()).isEqualTo("示政〔2026〕18号");
        callback(callbackPath, Map.of("key", typesetId, "status", 6, "url", editedUrl), SECRET)
                .andExpect(jsonPath("$.error").value(0));
        assertThat(latest(taskId).path("id").asText())
                .isEqualTo(edited.path("id").asText());
        byte[] saved = mockMvc.perform(get(
                                "/api/v1/writing-tasks/{taskId}/document/typesets/{id}/file",
                                taskId,
                                edited.path("id").asText())
                        .header("X-Tenant-Id", TENANT))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(saved))) {
            assertThat(word.getParagraphs().get(0).getText()).isEqualTo("在线编辑后的定稿");
        }
    }

    /** 文档服务器的保存回调：signWith 为空时不签名；签名放在请求体 token 字段（ONLYOFFICE 默认方式）。 */
    private ResultActions callback(String path, Map<String, Object> payload, String signWith) throws Exception {
        Map<String, Object> body = new HashMap<>(payload);
        if (signWith != null) {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.parse(payload));
            jwt.sign(new MACSigner(signWith.getBytes(StandardCharsets.UTF_8)));
            body.put("token", jwt.serialize());
        }
        return mockMvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode latest(String taskId) throws Exception {
        return data(mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/typesets/latest", taskId)
                .header("X-Tenant-Id", TENANT)));
    }

    private static String path(String url) {
        return URI.create(url).getRawPath();
    }

    private static HttpServer startDocumentServer() {
        try {
            byte[] edited;
            try (XWPFDocument document = new XWPFDocument();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.createParagraph().createRun().setText("在线编辑后的定稿");
                document.write(output);
                edited = output.toByteArray();
            }
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/cache/files/edited.docx", exchange -> {
                exchange.sendResponseHeaders(200, edited.length);
                exchange.getResponseBody().write(edited);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String createTaskWithOneParagraph() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"在线编辑请示","documentTypeCode":"请示","documentCategory":"法定公文",
                     "writingTopic":"申请资金","writingPurpose":"申请批准资金",
                     "expectedLengthMin":800,"expectedLengthMax":1600,"securityLevel":"内部"}
                    """)))
                .path("id")
                .asText();
        JsonNode outline = data(mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/outlines", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")));
        mockMvc.perform(post(
                                "/api/v1/writing-tasks/{taskId}/outlines/{version}/confirm",
                                taskId,
                                outline.path("version").asInt())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/section", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outlineNodeId\":\""
                                + outline.path("nodes").get(0).path("id").asText() + "\"}"))
                .andExpect(status().isOk());
        return taskId;
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
