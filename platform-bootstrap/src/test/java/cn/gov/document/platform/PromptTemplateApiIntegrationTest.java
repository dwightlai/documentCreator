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
class PromptTemplateApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void versionsTestsApprovesAndIsolatesPromptTemplates() throws Exception {
        String tenant = "tenant-prompt";
        String body = mockMvc.perform(
                        post("/api/v1/prompt-templates")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "prompt-admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
            {"templateCode":"PARAGRAPH-REQUEST","templateName":"请示段落生成","applicableScene":"段落生成","documentTypeCode":"请示",
             "applicableModels":["approved-model"],"systemRole":"你是政务公文写作助手，围绕{{topic}}写作。",
             "documentTypeMethod":"请示必须一文一事。","inputFields":{"topic":{"type":"string"}},
             "materialCitationRule":"仅引用授权材料。","antiFabricationRule":"禁止编造政策和数据。",
             "outputFormat":"只输出正文段落。","riskConstraint":"不替代审批决定。",
             "modelParameters":{"temperature":0.2,"top_p":0.8},"testSampleIds":["sample-001"],"changeDescription":"初始版本"}
            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("草稿"))
                .andExpect(jsonPath("$.data.modelParameters.temperature").value(0.2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/publish", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "prompt-admin"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/test", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "prompt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.unresolvedPlaceholders[0]").value("topic"));
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/test", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "prompt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"topic\":\"专项资金申请\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.renderedPrompt").value(org.hamcrest.Matchers.containsString("专项资金申请")));
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/submit-for-review", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "prompt-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("审核"));
        mockMvc.perform(post("/api/v1/prompt-templates/{id}/publish", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "approver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("生效"));
        mockMvc.perform(get("/api/v1/prompt-templates/{id}/versions", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "prompt-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].approvedBy").value("approver"))
                .andExpect(jsonPath("$.data[0].testResults").value(org.hamcrest.Matchers.containsString("promptHash")));
        mockMvc.perform(get("/api/v1/prompt-templates")
                        .header("X-Tenant-Id", "other-prompt-tenant")
                        .header("X-User-Id", "prompt-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
