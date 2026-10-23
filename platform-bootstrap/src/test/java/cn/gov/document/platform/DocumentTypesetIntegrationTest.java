package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A2 一键套红：无已发布模板时按内置标准版式排版，附格式检查结果；正文更新后定稿稿标记为需重新套红。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DocumentTypesetIntegrationTest {
    private static final String TENANT = "tenant-typeset";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void typesetsTheCurrentDocumentAndTracksStaleness() throws Exception {
        String taskId = createTaskWithOneParagraph();
        mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/typesets/latest", taskId)
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        typeset(taskId, Map.of("unknown_field", "x")).andExpect(status().isUnprocessableEntity());

        JsonNode typeset = data(typeset(
                taskId,
                Map.of(
                        "sender", "示范市人民政府文件",
                        "doc_number", "示政〔2026〕18号",
                        "signer", "张某",
                        "recipient", "省人民政府：",
                        "issue_unit", "示范市人民政府办公室")));
        assertThat(typeset.path("templateName").asText()).contains("内置标准版式");
        assertThat(typeset.path("templateId").isMissingNode()).isTrue();
        assertThat(typeset.path("source").asText()).isEqualTo("套红生成");
        assertThat(typeset.path("stale").asBoolean()).isFalse();
        assertThat(typeset.path("fieldValues").path("date").asText()).matches("\\d{4}年\\d{1,2}月\\d{1,2}日");
        assertThat(typeset.path("deviationCount").asInt())
                .isEqualTo(typeset.path("deviations").size());

        byte[] docx = mockMvc.perform(get(
                                "/api/v1/writing-tasks/{taskId}/document/typesets/{id}/file",
                                taskId,
                                typeset.path("id").asText())
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        JsonNode document = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{taskId}/document", taskId).header("X-Tenant-Id", TENANT)));
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = String.join(
                    "\n",
                    word.getParagraphs().stream().map(XWPFParagraph::getText).toList());
            assertThat(text)
                    .contains(
                            "示范市人民政府文件",
                            "示政〔2026〕18号　　签发人：张某",
                            document.path("title").asText(),
                            "省人民政府：")
                    .contains(document.path("paragraphs")
                            .get(0)
                            .path("plainText")
                            .asText()
                            .split("\n")[0])
                    .doesNotContain("{");
        }

        JsonNode paragraph = document.path("paragraphs").get(0);
        mockMvc.perform(put(
                                "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
                                taskId,
                                paragraph.path("id").asText())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "plainText",
                                paragraph.path("plainText").asText() + "补充。",
                                "lockVersion",
                                paragraph.path("lockVersion").asInt()))))
                .andExpect(status().isOk());
        JsonNode latest = data(mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/typesets/latest", taskId)
                .header("X-Tenant-Id", TENANT)));
        assertThat(latest.path("id").asText()).isEqualTo(typeset.path("id").asText());
        assertThat(latest.path("stale").asBoolean()).isTrue();

        mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/typesets/{id}/file", taskId, "missing")
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isNotFound());
    }

    private ResultActions typeset(String taskId, Map<String, String> values) throws Exception {
        return mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/typesets", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("values", values))));
    }

    private String createTaskWithOneParagraph() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"套红排版请示","documentTypeCode":"请示","documentCategory":"法定公文",
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

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
