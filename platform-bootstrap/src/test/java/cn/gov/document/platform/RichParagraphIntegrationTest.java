package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A7 富文本段落：服务端以富文本推导纯文本、拒绝白名单外内容、纯文本修改尽量保留格式、DOCX 导出保留表格和格式。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class RichParagraphIntegrationTest {
    private static final String TENANT = "tenant-rich-paragraph";
    private static final String RICH =
            """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"资金"},{"type":"text","text":"重点保障","marks":[{"type":"bold"}]}]},
              {"type":"table","content":[{"type":"tableRow","content":[
                {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"道路"}]}]},
                {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"120万元"}]}]}]}]}]}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void savesRichParagraphsAndKeepsFormattingThroughPlainTextEdits() throws Exception {
        String taskId = createTaskWithOneParagraph();
        JsonNode paragraph = currentParagraph(taskId);

        // 客户端提交的纯文本与富文本不一致时，以富文本推导的纯文本为准
        JsonNode saved = data(save(taskId, paragraph, "被忽略的纯文本", RICH));
        JsonNode richParagraph = saved.path("paragraphs").get(0);
        assertThat(richParagraph.path("plainText").asText()).isEqualTo("资金重点保障\n道路\t120万元");
        assertThat(richParagraph.path("contentOrigin").asText()).isEqualTo("人工录入");
        assertThat(objectMapper.readTree(richParagraph.path("richContent").asText()))
                .isEqualTo(objectMapper.readTree(RICH));

        save(taskId, richParagraph, "x", "{\"type\":\"doc\",\"content\":[{\"type\":\"image\"}]}")
                .andExpect(status().isUnprocessableEntity());

        // 只改纯文本且改动落在同一文本节点内（如采纳审校建议）：格式保留
        JsonNode rebased = data(save(taskId, richParagraph, "资金重点保障\n道路\t150万元", null))
                .path("paragraphs")
                .get(0);
        assertThat(rebased.path("plainText").asText()).isEqualTo("资金重点保障\n道路\t150万元");
        assertThat(rebased.path("richContent").asText()).contains("150万元").contains("\"bold\"");

        byte[] docx = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/export", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"DOCX\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(word.getTables()).hasSize(1);
            assertThat(word.getTables().get(0).getRow(0).getCell(1).getText()).isEqualTo("150万元");
            assertThat(word.getParagraphs())
                    .anyMatch(p -> p.getRuns().stream().anyMatch(r -> "重点保障".equals(r.text()) && r.isBold()));
        }

        // 改动无法落回富文本时降级为纯文本段落，避免富文本与纯文本不一致
        JsonNode plain =
                data(save(taskId, rebased, "全部改写为纯文本", null)).path("paragraphs").get(0);
        assertThat(plain.path("plainText").asText()).isEqualTo("全部改写为纯文本");
        assertThat(plain.path("richContent").isMissingNode()).isTrue();
    }

    private String createTaskWithOneParagraph() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"富文本请示","documentTypeCode":"请示","documentCategory":"法定公文",
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphs[0].richContent").doesNotExist());
        return taskId;
    }

    private JsonNode currentParagraph(String taskId) throws Exception {
        return data(mockMvc.perform(
                        get("/api/v1/writing-tasks/{taskId}/document", taskId).header("X-Tenant-Id", TENANT)))
                .path("paragraphs")
                .get(0);
    }

    private ResultActions save(String taskId, JsonNode paragraph, String text, String richContent) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("plainText", text);
        body.put("lockVersion", paragraph.path("lockVersion").asInt());
        if (richContent != null) body.put("richContent", richContent);
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
