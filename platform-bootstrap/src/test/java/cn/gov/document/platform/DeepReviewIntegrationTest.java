package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class DeepReviewIntegrationTest {
    private static final String TENANT = "tenant-deep-review";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void securityPlanReportsFactLogicAndSafetyIssuesThatCannotBeAutoAccepted() throws Exception {
        String taskId = createDocument(
                "全市共建成服务站28个，其中城区15个、乡镇16个。投诉量由80件下降到96件，下降20%。" + "截止目前为止进展顺利，截止目前为止未发现问题。联系人身份证号11010519491231002X。");
        String planId = null;
        for (JsonNode plan : data(mockMvc.perform(get("/api/v1/review-plans").header("X-Tenant-Id", TENANT)))) {
            if ("SECURITY".equals(plan.path("planCode").asText()))
                planId = plan.path("id").asText();
        }
        assertThat(planId).isNotNull();

        String reviewId = data(mockMvc.perform(post("/api/v1/review-tasks")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("writingTaskId", taskId, "reviewPlanId", planId)))))
                .path("id")
                .asText();
        JsonNode issues = data(mockMvc.perform(
                get("/api/v1/review-tasks/{id}/issues", reviewId).header("X-Tenant-Id", TENANT)));

        List<String> methods = new ArrayList<>();
        issues.forEach(issue -> methods.add(issue.path("detectionMethod").asText()));
        assertThat(methods).contains("规则-L5", "规则-L6", "规则-L7");

        JsonNode fact = find(issues, "规则-L5", "共建成服务站28个");
        assertThat(fact.path("suggestedFix").isMissingNode()
                        || fact.path("suggestedFix").isNull())
                .isTrue();
        assertThat(fact.path("autoFixAllowed").asBoolean()).isFalse();
        assertThat(fact.path("issueExplanation").asText()).contains("15 + 16 = 31个");
        assertThat(find(issues, "规则-L7", "11010519491231002X").path("riskLevel").asText())
                .isEqualTo("严重");

        // 同一错词出现两次，归入同一合并组，且允许一键采纳
        List<JsonNode> repeated = new ArrayList<>();
        issues.forEach(issue -> {
            if ("截止目前为止".equals(issue.path("originalText").asText())) repeated.add(issue);
        });
        assertThat(repeated).hasSize(2);
        assertThat(repeated.get(0).path("mergedGroupId").asText())
                .isNotBlank()
                .isEqualTo(repeated.get(1).path("mergedGroupId").asText());
        assertThat(repeated.get(0).path("autoFixAllowed").asBoolean()).isTrue();

        // 同一段落内同组问题可一次批量采纳：第一次采纳产生新版本后，第二处仍能定位
        mockMvc.perform(post("/api/v1/review-tasks/{id}/issues/batch-accept", reviewId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "issueNumbers",
                                List.of(
                                        repeated.get(0).path("issueNumber").asText(),
                                        repeated.get(1).path("issueNumber").asText())))))
                .andExpect(status().isOk());
        JsonNode accepted = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{taskId}/document", taskId).header("X-Tenant-Id", TENANT)));
        assertThat(accepted.path("paragraphs").get(0).path("plainText").asText())
                .contains("截至目前进展顺利，截至目前未发现问题")
                .doesNotContain("截止目前为止");

        // 事实类问题禁止自动采纳（§21）
        mockMvc.perform(patch(
                                "/api/v1/review-tasks/{id}/issues/{number}/accept",
                                reviewId,
                                fact.path("issueNumber").asText())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private JsonNode find(JsonNode issues, String method, String original) {
        for (JsonNode issue : issues) {
            if (method.equals(issue.path("detectionMethod").asText())
                    && original.equals(issue.path("originalText").asText())) return issue;
        }
        throw new AssertionError("未找到问题：" + method + " " + original);
    }

    private String createDocument(String text) throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "writer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"深度审校样稿","documentTypeCode":"报告","documentCategory":"法定公文",
                     "writingTopic":"服务站建设情况","writingPurpose":"报告建设进展",
                     "expectedLengthMin":800,"expectedLengthMax":1600,"securityLevel":"内部"}
                    """)))
                .path("id")
                .asText();
        JsonNode outline = data(mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/outlines", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        data(mockMvc.perform(post(
                        "/api/v1/writing-tasks/{taskId}/outlines/{version}/confirm",
                        taskId,
                        outline.path("version").asInt())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        JsonNode document = data(mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/section", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outlineNodeId\":\""
                        + outline.path("nodes").get(0).path("id").asText() + "\"}")));
        JsonNode paragraph = document.path("paragraphs").get(0);
        data(mockMvc.perform(put(
                        "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
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
        return taskId;
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
