package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyApiIntegrationTest {
    private static final String TENANT = "tenant-idempotency";
    private static final String KEY = "model-create-0001";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void replaysFirstResponseAndRejectsKeyReuseWithDifferentBody() throws Exception {
        String requestBody = configBody("IDEMPOTENT-ONE", "first-model");
        MvcResult first = create(TENANT, KEY, requestBody)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andReturn();

        MvcResult replay = create(TENANT, KEY, requestBody)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();

        assertThat(replay.getResponse().getContentAsByteArray())
                .isEqualTo(first.getResponse().getContentAsByteArray());

        create(TENANT, KEY, configBody("IDEMPOTENT-TWO", "second-model"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同一 Idempotency-Key 不能用于不同请求"));

        mockMvc.perform(get("/api/v1/model-configs")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "idempotency-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        create("another-idempotency-tenant", KEY, requestBody).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions create(String tenant, String key, String body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/model-configs")
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", "idempotency-admin")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String configBody(String code, String modelName) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.ofEntries(
                java.util.Map.entry("configCode", code),
                java.util.Map.entry("configName", "幂等模型"),
                java.util.Map.entry("provider", "境内云端"),
                java.util.Map.entry("modelName", modelName),
                java.util.Map.entry("scene", "paragraph"),
                java.util.Map.entry("dataLevel", "内部"),
                java.util.Map.entry("maxContextTokens", 4096),
                java.util.Map.entry("timeoutMs", 30000),
                java.util.Map.entry("maxRetries", 1),
                java.util.Map.entry("sensitiveWordFilter", true),
                java.util.Map.entry("outputValidation", true),
                java.util.Map.entry("active", true)));
    }
}
