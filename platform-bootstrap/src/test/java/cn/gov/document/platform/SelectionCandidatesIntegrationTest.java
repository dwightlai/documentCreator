package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** A4 划词改写：候选只生成不落稿；采用候选的段落标记为 AI 改写，且只能采用为该段落生成的候选。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SelectionCandidatesIntegrationTest {
    private static final String TENANT = "tenant-selection-candidates";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void generatesCandidatesWithoutSavingAndMarksAdoptingParagraphAsAiRewritten() throws Exception {
        String taskId = createTaskWithTwoParagraphs();
        JsonNode before = document(taskId);
        JsonNode first = before.path("paragraphs").get(0);
        JsonNode second = before.path("paragraphs").get(1);

        // 规则引擎兜底时三种风格结果相同，去重后只返回一个候选；正文版本不变
        JsonNode result = data(candidates(taskId, first, "改写", "马上搞好"));
        assertThat(result.path("candidates")).hasSize(1);
        JsonNode candidate = result.path("candidates").get(0);
        assertThat(candidate.path("text").asText()).isEqualTo("及时做好");
        assertThat(candidate.path("style").asText()).isEqualTo("规范庄重");
        assertThat(document(taskId).path("currentVersion").asInt())
                .isEqualTo(before.path("currentVersion").asInt());
        String generationId = candidate.path("generationId").asText();

        // 候选只能用于生成它的段落；不存在的候选同样拒绝
        save(taskId, second, second.path("plainText").asText() + "及时做好", List.of(generationId))
                .andExpect(status().isUnprocessableEntity());
        save(taskId, first, first.path("plainText").asText(), List.of("no-such-generation"))
                .andExpect(status().isUnprocessableEntity());

        JsonNode saved = data(save(taskId, first, first.path("plainText").asText() + "及时做好", List.of(generationId)));
        assertThat(saved.path("paragraphs").get(0).path("contentOrigin").asText())
                .isEqualTo("AI改写");
        // 不采用候选的人工修改仍记为人工录入；页面停留在旧版本时候选按位置映射到最新段落
        JsonNode manual = data(save(taskId, second, second.path("plainText").asText() + "补充", null));
        assertThat(manual.path("paragraphs").get(1).path("contentOrigin").asText())
                .isEqualTo("人工录入");

        candidates(taskId, first, "翻译", "马上搞好").andExpect(status().is4xxClientError());
        mockMvc.perform(post(
                                "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}/lock",
                                taskId,
                                manual.path("paragraphs").get(0).path("id").asText())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice"))
                .andExpect(status().isOk());
        candidates(taskId, manual.path("paragraphs").get(0), "润色", "及时做好").andExpect(status().isConflict());
    }

    private String createTaskWithTwoParagraphs() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"划词改写请示","documentTypeCode":"请示","documentCategory":"法定公文",
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

    private ResultActions candidates(String taskId, JsonNode paragraph, String operation, String selected)
            throws Exception {
        return mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/selection-candidates", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "paragraphId", paragraph.path("id").asText(),
                        "operation", operation,
                        "selectedText", selected))));
    }

    private ResultActions save(String taskId, JsonNode paragraph, String text, List<String> adopted) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("plainText", text);
        body.put("lockVersion", paragraph.path("lockVersion").asInt());
        if (adopted != null) body.put("adoptedGenerationIds", adopted);
        return mockMvc.perform(put(
                        "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
                        taskId,
                        paragraph.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
