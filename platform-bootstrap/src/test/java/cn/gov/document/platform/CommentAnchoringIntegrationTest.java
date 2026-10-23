package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 正文批注：记录选中文字，段落保存形成新版本后仍能映射到当前版本的同一段落。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CommentAnchoringIntegrationTest {
    private static final String TENANT = "tenant-comment-anchoring";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void commentsFollowTheirParagraphAcrossVersions() throws Exception {
        String taskId = createTaskWithOneParagraph();
        JsonNode document = document(taskId);
        String documentId = document.path("id").asText();
        JsonNode paragraph = document.path("paragraphs").get(0);

        JsonNode comment = data(mockMvc.perform(post("/api/v1/documents/{documentId}/comments", documentId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "reviewer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "paragraphId", paragraph.path("id").asText(),
                        "commentType", "意见",
                        "commentText", "请补充资金测算依据",
                        "quotedText", "  专项建设资金  ")))));
        assertThat(comment.path("quotedText").asText()).isEqualTo("专项建设资金");
        assertThat(comment.path("currentParagraphId").asText())
                .isEqualTo(paragraph.path("id").asText());

        JsonNode saved = data(mockMvc.perform(put(
                        "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
                        taskId,
                        paragraph.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "plainText",
                        paragraph.path("plainText").asText() + "资金测算见附件。",
                        "lockVersion",
                        paragraph.path("lockVersion").asInt())))));
        String newParagraphId = saved.path("paragraphs").get(0).path("id").asText();
        assertThat(newParagraphId).isNotEqualTo(paragraph.path("id").asText());

        JsonNode listed = data(mockMvc.perform(get("/api/v1/documents/{documentId}/comments", documentId)
                .header("X-Tenant-Id", TENANT)
                .param("resolved", "false")));
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).path("paragraphId").asText())
                .isEqualTo(paragraph.path("id").asText());
        assertThat(listed.get(0).path("currentParagraphId").asText()).isEqualTo(newParagraphId);

        JsonNode resolved = data(mockMvc.perform(put(
                        "/api/v1/documents/{documentId}/comments/{commentId}/resolve",
                        documentId,
                        comment.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")));
        assertThat(resolved.path("resolved").asBoolean()).isTrue();
        assertThat(resolved.path("currentParagraphId").asText()).isEqualTo(newParagraphId);
    }

    private String createTaskWithOneParagraph() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"批注定位请示","documentTypeCode":"请示","documentCategory":"法定公文",
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
        mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/section", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outlineNodeId\":\""
                                + outline.path("nodes").get(0).path("id").asText() + "\"}"))
                .andExpect(status().isOk());
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
