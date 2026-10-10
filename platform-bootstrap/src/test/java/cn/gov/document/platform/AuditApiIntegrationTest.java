package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuditApiIntegrationTest {
    private static final String TENANT = "tenant-audit";
    private static final String SENSITIVE_VALUE = "audit-secret-13812345678";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void appendsMetadataOnlyAuditAndKeepsTenantIsolation() throws Exception {
        mockMvc.perform(post("/api/v1/model-configs")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "audit-admin")
                        .header("X-Forwarded-For", "10.1.2.3, 10.2.3.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "configCode":"AUDIT-CLOUD",
                                  "configName":"%s",
                                  "provider":"cloud",
                                  "modelName":"safe-model",
                                  "scene":"paragraph",
                                  "dataLevel":"\u5185\u90e8",
                                  "maxContextTokens":4096,
                                  "timeoutMs":30000,
                                  "maxRetries":1,
                                  "sensitiveWordFilter":true,
                                  "outputValidation":true,
                                  "active":true
                                }
                                """
                                        .formatted(SENSITIVE_VALUE)))
                .andExpect(status().isOk());

        String auditResponse = mockMvc.perform(
                        get("/api/v1/audit-logs").header("X-Tenant-Id", TENANT).header("X-User-Id", "audit-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("POST"))
                .andExpect(jsonPath("$.data[0].targetType").value("model-configs"))
                .andExpect(jsonPath("$.data[0].ipAddress").value("10.1.2.3"))
                .andExpect(jsonPath("$.data[0].detail.requestBodyLogged").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode audit = objectMapper.readTree(auditResponse).path("data").get(0);
        assertThat(audit.path("detail").asText())
                .contains("\"requestBodyLogged\":false")
                .doesNotContain(SENSITIVE_VALUE);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("X-Tenant-Id", "other-audit-tenant")
                        .header("X-User-Id", "other-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
