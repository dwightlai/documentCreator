package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OaWritingSubmissionIntegrationTest {
    private static final String TENANT = "tenant-oa-writing";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void oaInitiatedWritingMustPassSubmissionGateBeforeSubmitting() throws Exception {
        String request =
                """
                {"businessId":"DOC-2026-00128","documentType":"通知","taskName":"关于开展安全检查的通知",
                 "writingTopic":"开展安全生产检查","writingPurpose":"部署检查工作","draftingDepartment":"办公室",
                 "materials":[{"materialName":"检查方案要点","content":"检查范围为全市重点企业。"}]}
                """;
        JsonNode created = data(mockMvc.perform(post("/api/v1/integrations/oa/writing-tasks")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "oa-system")
                        .header("Idempotency-Key", "oa-writing-00128")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated()));
        String taskId = created.path("writingTask").path("id").asText();
        assertThat(created.path("writingTask").path("sourceSystem").asText()).isEqualTo("OA");
        assertThat(created.path("writingTask").path("businessId").asText()).isEqualTo("DOC-2026-00128");
        assertThat(created.path("materialCount").asInt()).isEqualTo(1);
        mockMvc.perform(post("/api/v1/integrations/oa/writing-tasks")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "oa-system")
                        .header("Idempotency-Key", "oa-writing-00128-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
        JsonNode materials = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{id}/materials", taskId).header("X-Tenant-Id", TENANT)));
        assertThat(materials.get(0).path("sourceType").asText()).isEqualTo("OA关联");

        String paragraphId = draft(taskId);
        JsonNode gate = check(taskId);
        assertThat(gate.path("passed").asBoolean()).isFalse();
        assertThat(failedRules(gate)).contains("REVIEW");
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/submit-to-oa", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "writer"))
                .andExpect(status().isUnprocessableEntity());

        String reviewId = review(taskId);
        assertThat(failedRules(check(taskId))).containsExactly("REPORT");
        report(reviewId);
        assertThat(check(taskId).path("passed").asBoolean()).isTrue();

        // 审校后又有人工修改：必须重新审校
        JsonNode document = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{id}/document", taskId).header("X-Tenant-Id", TENANT)));
        save(taskId, document.path("paragraphs").get(0), "各单位要按照方案要求开展检查。");
        assertThat(failedRules(check(taskId))).containsExactly("LATEST_REVIEW");

        report(review(taskId));
        JsonNode submitted = data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/submit-to-oa", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        int version = submitted.path("documentVersion").asInt();
        assertThat(submitted.path("businessId").asText()).isEqualTo("DOC-2026-00128");
        assertThat(submitted.path("gate").path("passed").asBoolean()).isTrue();
        // 提交版本被保护，不会被版本保留策略清理
        boolean protectedVersion = false;
        for (JsonNode v : data(mockMvc.perform(
                get("/api/v1/writing-tasks/{id}/document/versions", taskId).header("X-Tenant-Id", TENANT)))) {
            if (v.path("versionNumber").asInt() == version)
                protectedVersion = v.path("protectedVersion").asBoolean();
        }
        assertThat(protectedVersion).isTrue();
        assertThat(paragraphId).isNotBlank();
    }

    @Test
    void gateRulesCanBeRelaxedPerTenant() throws Exception {
        String tenant = "tenant-oa-gate-config";
        JsonNode updated = data(
                mockMvc.perform(
                        put("/api/v1/submission-gate")
                                .header("X-Tenant-Id", tenant)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"requireReview":true,"requireLatestReview":true,"blockOnSevere":true,
                         "requireImportantProcessed":true,"requireReport":false}
                        """)));
        assertThat(updated.path("requireReport").asBoolean()).isFalse();
        assertThat(data(mockMvc.perform(get("/api/v1/submission-gate").header("X-Tenant-Id", tenant)))
                        .path("requireReport")
                        .asBoolean())
                .isFalse();
        assertThat(data(mockMvc.perform(get("/api/v1/submission-gate").header("X-Tenant-Id", "tenant-other-gate")))
                        .path("requireReport")
                        .asBoolean())
                .isTrue();
    }

    private String draft(String taskId) throws Exception {
        JsonNode outline = data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/outlines", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        data(mockMvc.perform(post(
                        "/api/v1/writing-tasks/{id}/outlines/{v}/confirm",
                        taskId,
                        outline.path("version").asInt())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        JsonNode document = data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/generate/section", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outlineNodeId\":\""
                        + outline.path("nodes").get(0).path("id").asText() + "\"}")));
        // 使用无审校问题的正文，避免规则命中影响门禁断言
        JsonNode saved = save(taskId, document.path("paragraphs").get(0), "各单位要按照检查方案认真开展安全检查。");
        return saved.path("paragraphs").get(0).path("id").asText();
    }

    private JsonNode save(String taskId, JsonNode paragraph, String text) throws Exception {
        return data(mockMvc.perform(put(
                        "/api/v1/writing-tasks/{id}/document/paragraphs/{pid}",
                        taskId,
                        paragraph.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "plainText",
                        text,
                        "lockVersion",
                        paragraph.path("lockVersion").asInt())))));
    }

    private String review(String taskId) throws Exception {
        return data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/initiate-review", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer")))
                .path("id")
                .asText();
    }

    private void report(String reviewId) throws Exception {
        data(mockMvc.perform(post("/api/v1/review-tasks/{id}/reports", reviewId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "reviewer")));
    }

    private JsonNode check(String taskId) throws Exception {
        return data(mockMvc.perform(
                get("/api/v1/writing-tasks/{id}/submission-check", taskId).header("X-Tenant-Id", TENANT)));
    }

    private static List<String> failedRules(JsonNode gate) {
        List<String> failed = new ArrayList<>();
        gate.path("rules").forEach(rule -> {
            if (!rule.path("passed").asBoolean()) failed.add(rule.path("code").asText());
        });
        return failed;
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body = actions.andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
