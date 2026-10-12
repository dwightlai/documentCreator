package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AiDisclosureIntegrationTest {
    private static final String TENANT = "tenant-ai-disclosure";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void labelsParagraphOriginAndWritesDisclosureIntoDocx() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "writer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"AI标识样稿","documentTypeCode":"通知","documentCategory":"法定公文",
                     "writingTopic":"开展检查","writingPurpose":"部署检查工作",
                     "expectedLengthMin":500,"expectedLengthMax":1000,"securityLevel":"内部"}
                    """)))
                .path("id")
                .asText();
        JsonNode outline = data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/outlines", taskId)
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        data(mockMvc.perform(post(
                        "/api/v1/writing-tasks/{id}/outlines/{v}/confirm",
                        taskId,
                        outline.path("version").asInt())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")));
        JsonNode document = null;
        for (int i = 0; i < 2; i++) {
            document = data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/generate/section", taskId)
                    .header("X-Tenant-Id", TENANT)
                    .header("X-User-Id", "writer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"outlineNodeId\":\""
                            + outline.path("nodes").get(i).path("id").asText() + "\"}")));
        }
        assertThat(document.path("paragraphs").get(0).path("contentOrigin").asText())
                .isEqualTo("AI生成");
        JsonNode first = document.path("paragraphs").get(0);
        document = data(mockMvc.perform(put(
                        "/api/v1/writing-tasks/{id}/document/paragraphs/{pid}",
                        taskId,
                        first.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "plainText",
                        "人工撰写的开头段落。",
                        "lockVersion",
                        first.path("lockVersion").asInt())))));
        assertThat(document.path("paragraphs").get(0).path("contentOrigin").asText())
                .isEqualTo("人工录入");

        String manual = document.path("paragraphs").get(0).path("plainText").asText();
        String generated = document.path("paragraphs").get(1).path("plainText").asText();
        BigDecimal expectedRatio = BigDecimal.valueOf(generated.codePointCount(0, generated.length()))
                .divide(
                        BigDecimal.valueOf(manual.codePointCount(0, manual.length())
                                + generated.codePointCount(0, generated.length())),
                        4,
                        RoundingMode.HALF_UP);
        JsonNode disclosure = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{id}/document/ai-disclosure", taskId).header("X-Tenant-Id", TENANT)));
        assertThat(disclosure.path("aiGeneratedRatio").decimalValue()).isEqualByComparingTo(expectedRatio);
        assertThat(disclosure.path("generationRecordsCount").asLong()).isEqualTo(2);

        byte[] docx = mockMvc.perform(post("/api/v1/writing-tasks/{id}/document/export", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"DOCX\",\"retainCitations\":false,\"aiDisclosure\":true}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(docx))) {
            var properties = word.getProperties().getCustomProperties();
            assertThat(new BigDecimal(
                            properties.getProperty("ai_generated_ratio").getLpwstr()))
                    .isEqualByComparingTo(expectedRatio);
            assertThat(properties.getProperty("generation_records_count").getLpwstr())
                    .isEqualTo("2");
            // 只有 AI 生成的第二段带来源批注，人工录入段落不打标
            assertThat(word.getDocComments().getComments()).hasSize(1);
            assertThat(word.getDocComments().getComments().get(0).getText()).contains("来源类型：AI生成");
            List<XWPFParagraph> paragraphs = word.getParagraphs();
            assertThat(paragraphs.get(paragraphs.size() - 1).getText()).startsWith("AI辅助生成说明：");
        }
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
