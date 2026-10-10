package cn.gov.document.platform;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DocumentTypeConfigApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publishesVersionedConfigAndAppliesItToOutlineGeneration() throws Exception {
        String tenant = "tenant-type-config";
        String createBody = mockMvc.perform(
                        post("/api/v1/document-type-configs")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "config-admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"typeCode":"请示","typeName":"请示","documentCategory":"法定公文",
                         "applicableScenario":"向上级机关请求指示、批准","direction":"上行",
                         "requiredFields":["主送机关","请示事项","请示理由"],
                         "recommendedStructure":["请示背景与依据","具体事项","请求内容","结束语"],
                         "forbiddenPatterns":["一文多事","命令式语气"],
                         "closingPatterns":["妥否，请批示。"],"changeDescription":"建立单位请示规范"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("草稿"))
                .andExpect(jsonPath("$.data.recommendedStructure.length()").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int lockVersion = objectMapper
                .readTree(createBody)
                .path("data")
                .path("lockVersion")
                .asInt();

        mockMvc.perform(post("/api/v1/document-type-configs/{code}/publish", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "config-admin"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/document-type-configs/{code}/test", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "config-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true));
        mockMvc.perform(post("/api/v1/document-type-configs/{code}/submit-for-review", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "config-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("审核"));
        mockMvc.perform(post("/api/v1/document-type-configs/{code}/publish", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "approver-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("生效"));
        mockMvc.perform(get("/api/v1/document-type-configs/{code}/versions", "请示")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].approvedBy").value("approver-1"));

        String taskBody = mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "writer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"taskName":"配置驱动请示","documentTypeCode":"请示","documentCategory":"法定公文",
                         "writingTopic":"申请建设政务平台","expectedLengthMin":1000,"expectedLengthMax":2000}
                        """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId = objectMapper.readTree(taskBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/outlines", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "writer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(4))
                .andExpect(jsonPath("$.data.nodes[2].title").value("请求内容"))
                .andExpect(jsonPath("$.data.nodes[3].title").value("结束语"));

        mockMvc.perform(put("/api/v1/document-type-configs/{code}", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "config-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"typeName":"请示","documentCategory":"法定公文","direction":"上行",
                         "requiredFields":["主送机关"],"recommendedStructure":["正文"],
                         "forbiddenPatterns":[],"closingPatterns":["妥否，请批示。"],
                         "changeDescription":"不应允许的发布后编辑","lockVersion":%d}
                        """
                                        .formatted(lockVersion)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/document-type-configs").header("X-Tenant-Id", "other-type-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(23))
                .andExpect(jsonPath("$.data[?(@.typeCode == '请示')].id").value(hasItem("builtin-请示")));
    }
}
