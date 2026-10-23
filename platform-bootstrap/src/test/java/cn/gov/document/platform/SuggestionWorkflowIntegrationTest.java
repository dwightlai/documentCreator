package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
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

/** A7 修订模式：修订不直接改正文；逐条接受形成新版本或拒绝；原文已变化的修订过期，不能接受。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SuggestionWorkflowIntegrationTest {
    private static final String TENANT = "tenant-suggestion";
    private static final String BOLD =
            "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"修订后的第一段\",\"marks\":[{\"type\":\"bold\"}]}]}]}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void suggestionsAreAcceptedOrRejectedOneByOneAndExpireWhenTheParagraphChanges() throws Exception {
        String taskId = createTaskWithTwoParagraphs();
        JsonNode before = document(taskId);
        JsonNode first = before.path("paragraphs").get(0);
        JsonNode second = before.path("paragraphs").get(1);

        JsonNode bob = data(propose(taskId, "bob", first, "忽略", BOLD, null));
        assertThat(bob.path("status").asText()).isEqualTo("待处理");
        assertThat(bob.path("stale").asBoolean()).isFalse();
        assertThat(bob.path("proposedPlainText").asText()).isEqualTo("修订后的第一段");
        assertThat(bob.path("basePlainText").asText())
                .isEqualTo(first.path("plainText").asText());
        assertThat(bob.path("currentParagraphId").asText())
                .isEqualTo(first.path("id").asText());
        assertThat(document(taskId).path("currentVersion").asInt())
                .isEqualTo(before.path("currentVersion").asInt());
        propose(taskId, "bob", first, first.path("plainText").asText(), null, null)
                .andExpect(status().isUnprocessableEntity());
        JsonNode carol = data(propose(taskId, "carol", first, "carol 的另一种改法", null, null));

        // 接受 bob 的修订：形成新版本，段落内容和格式来自修订，来源为人工录入
        JsonNode accepted = data(decide(taskId, "alice", bob, "accept"));
        JsonNode firstNow = accepted.path("paragraphs").get(0);
        assertThat(accepted.path("currentVersion").asInt())
                .isEqualTo(before.path("currentVersion").asInt() + 1);
        assertThat(firstNow.path("plainText").asText()).isEqualTo("修订后的第一段");
        assertThat(firstNow.path("richContent").asText()).contains("bold");
        assertThat(firstNow.path("contentOrigin").asText()).isEqualTo("人工录入");
        JsonNode versions = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{taskId}/document/versions", taskId).header("X-Tenant-Id", TENANT)));
        assertThat(versions.toString()).contains("修订接受").contains("接受 bob 的修订");
        decide(taskId, "alice", bob, "accept").andExpect(status().isConflict());

        // 同一段落的另一条修订原文已变化：标记过期，不能接受，只能拒绝
        JsonNode pending = data(mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/suggestions", taskId)
                .header("X-Tenant-Id", TENANT)
                .param("status", "待处理")));
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).path("id").asText())
                .isEqualTo(carol.path("id").asText());
        assertThat(pending.get(0).path("stale").asBoolean()).isTrue();
        assertThat(pending.get(0).path("currentParagraphId").asText())
                .isEqualTo(firstNow.path("id").asText());
        decide(taskId, "alice", carol, "accept").andExpect(status().isConflict());
        JsonNode rejected = data(decide(taskId, "alice", carol, "reject"));
        assertThat(rejected.path("status").asText()).isEqualTo("已拒绝");
        assertThat(rejected.path("decidedBy").asText()).isEqualTo("alice");
        assertThat(rejected.path("stale").asBoolean()).isFalse();

        // 修订中采用了划词改写候选：接受后段落标记为 AI 改写
        JsonNode candidate = data(mockMvc.perform(
                        post("/api/v1/writing-tasks/{taskId}/generate/selection-candidates", taskId)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "bob")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "paragraphId", second.path("id").asText(),
                                        "operation", "改写",
                                        "selectedText", "马上搞好")))))
                .path("candidates")
                .get(0);
        JsonNode aiSuggestion = data(propose(
                taskId,
                "bob",
                second,
                second.path("plainText").asText() + "及时做好",
                null,
                List.of(candidate.path("generationId").asText())));
        assertThat(aiSuggestion.path("aiAssisted").asBoolean()).isTrue();
        JsonNode afterAi = data(decide(taskId, "alice", aiSuggestion, "accept"));
        assertThat(afterAi.path("paragraphs").get(1).path("contentOrigin").asText())
                .isEqualTo("AI改写");

        // 锁定段落不能提出修订
        mockMvc.perform(post(
                                "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}/lock",
                                taskId,
                                afterAi.path("paragraphs").get(1).path("id").asText())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice"))
                .andExpect(status().isOk());
        propose(taskId, "bob", afterAi.path("paragraphs").get(1), "锁定段落的修订", null, null)
                .andExpect(status().isConflict());
    }

    private ResultActions propose(
            String taskId, String user, JsonNode paragraph, String text, String rich, List<String> adopted)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("paragraphId", paragraph.path("id").asText());
        body.put("lockVersion", paragraph.path("lockVersion").asInt());
        body.put("plainText", text);
        if (rich != null) body.put("richContent", rich);
        if (adopted != null) body.put("adoptedGenerationIds", adopted);
        return mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/suggestions", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions decide(String taskId, String user, JsonNode suggestion, String action) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/writing-tasks/{taskId}/document/suggestions/{id}/" + action,
                        taskId,
                        suggestion.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", user));
    }

    private String createTaskWithTwoParagraphs() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"修订模式请示","documentTypeCode":"请示","documentCategory":"法定公文",
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
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/section", taskId)
                            .header("X-Tenant-Id", TENANT)
                            .header("X-User-Id", "alice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"outlineNodeId\":\""
                                    + outline.path("nodes").get(i).path("id").asText() + "\"}"))
                    .andExpect(status().isOk());
        }
        return taskId;
    }

    private JsonNode document(String taskId) throws Exception {
        return data(mockMvc.perform(
                get("/api/v1/writing-tasks/{taskId}/document", taskId).header("X-Tenant-Id", TENANT)));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
