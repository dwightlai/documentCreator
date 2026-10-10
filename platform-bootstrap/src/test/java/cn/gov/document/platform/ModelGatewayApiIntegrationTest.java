package cn.gov.document.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class ModelGatewayApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void routesByDataLevelAndRedactsCloudInput() throws Exception {
        String tenant = "tenant-model";
        String cloud = mockMvc.perform(
                        post("/api/v1/model-configs")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"configCode\":\"CLOUD-WRITE\",\"configName\":\"云端写作模型\",\"provider\":\"境内云端\",\"modelName\":\"approved-model\",\"scene\":\"段落生成\",\"dataLevel\":\"内部\",\"maxContextTokens\":8192,\"timeoutMs\":30000,\"maxRetries\":2,\"sensitiveWordFilter\":true,\"outputValidation\":true,\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cloudId = objectMapper.readTree(cloud).path("data").path("id").asText();
        mockMvc.perform(
                        post("/api/v1/model-gateway/route-preview")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"scene\":\"段落生成\",\"dataLevel\":\"内部\",\"input\":\"联系人手机号13812345678，邮箱test@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelConfigId").value(cloudId))
                .andExpect(jsonPath("$.data.cloudRoute").value(true))
                .andExpect(jsonPath("$.data.redactedEntityCount").value(2))
                .andExpect(jsonPath("$.data.preparedInput").value("联系人手机号[已脱敏]，邮箱[已脱敏]"));
        mockMvc.perform(
                        post("/api/v1/model-configs")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"configCode\":\"LOCAL-SAFE\",\"configName\":\"私有安全模型\",\"provider\":\"本地私有\",\"modelName\":\"local-model\",\"scene\":\"段落生成\",\"dataLevel\":\"敏感\",\"maxContextTokens\":8192,\"timeoutMs\":60000,\"maxRetries\":1,\"sensitiveWordFilter\":false,\"outputValidation\":true,\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/model-gateway/route-preview")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"段落生成\",\"dataLevel\":\"敏感\",\"input\":\"内部敏感事项\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("本地私有"))
                .andExpect(jsonPath("$.data.cloudRoute").value(false));
        mockMvc.perform(post("/api/v1/model-gateway/route-preview")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"段落生成\",\"dataLevel\":\"涉密\",\"input\":\"涉密事项\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/model-configs")
                        .header("X-Tenant-Id", "other-tenant")
                        .header("X-User-Id", "admin-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
