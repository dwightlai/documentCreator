package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "platform.model.circuit-breaker.failure-threshold=2",
            "platform.model.circuit-breaker.open-duration=10m"
        })
@AutoConfigureMockMvc
class ModelFailoverIntegrationTest {
    private static final String TENANT = "tenant-model-failover";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void fallsBackToBackupModelRestoresRedactedValuesAndOpensCircuitForFailingModel() throws Exception {
        AtomicInteger primaryCalls = new AtomicInteger();
        HttpServer primary = server(exchange -> {
            primaryCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        HttpServer backup = server(exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response =
                    "{\"choices\":[{\"message\":{\"content\":\"请联系[已脱敏-1]确认\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            String primaryId = createModel("A-PRIMARY", endpoint(primary));
            String backupId = createModel("B-BACKUP", endpoint(backup));
            publishPrompt();

            for (int call = 0; call < 3; call++) {
                mockMvc.perform(
                                post("/api/v1/model-gateway/invoke")
                                        .header("X-Tenant-Id", TENANT)
                                        .header("X-User-Id", "writer")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                        {"scene":"段落生成","dataLevel":"普通","documentTypeCode":"请示","input":"联系人13812345678，请形成初稿。",
                         "variables":{},"taskType":"写作","taskId":"task-failover"}
                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.modelConfigId").value(backupId))
                        .andExpect(jsonPath("$.data.output").value("请联系13812345678确认"));
            }
            // 阈值为 2：前两次调用主模型失败后熔断，第三次直接路由到备用模型
            assertThat(primaryCalls.get()).isEqualTo(2);

            mockMvc.perform(get("/api/v1/model-call-logs")
                            .param("taskId", "task-failover")
                            .header("X-Tenant-Id", TENANT)
                            .header("X-User-Id", "admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.modelConfigId=='%s' && @.success==false)]".formatted(primaryId))
                            .isNotEmpty());
        } finally {
            primary.stop(0);
            backup.stop(0);
        }
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", handler);
        server.start();
        return server;
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
    }

    private String createModel(String code, String endpoint) throws Exception {
        String body = mockMvc.perform(post("/api/v1/model-configs")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                {"configCode":"%s","configName":"%s","provider":"境内云端","modelName":"mock-model","modelVersion":"test-v1",
                 "scene":"段落生成","dataLevel":"内部","maxContextTokens":4096,"timeoutMs":5000,"maxRetries":0,
                 "costLimitDaily":10,"sensitiveWordFilter":true,"outputValidation":true,"active":true,
                 "endpointUrl":"%s","inputCostPer1k":0.01,"outputCostPer1k":0.02,"qpsLimit":50,"dailyTokenLimit":100000}
                """
                                        .formatted(code, code, endpoint)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private void publishPrompt() throws Exception {
        String body = mockMvc.perform(
                        post("/api/v1/prompt-templates")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                {"templateCode":"FAILOVER-PROMPT","templateName":"备用模型提示词","applicableScene":"段落生成","documentTypeCode":"请示",
                 "applicableModels":["mock-model"],"systemRole":"形成请示段落。业务输入：{{input}}","inputFields":{},
                 "antiFabricationRule":"不得编造。","outputFormat":"仅输出正文。","modelParameters":{"temperature":0.1}}
                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String promptId = objectMapper.readTree(body).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/test", promptId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"input\":\"测试业务内容\"}}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/submit-for-review", promptId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/publish", promptId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "approver"))
                .andExpect(status().isOk());
    }
}
