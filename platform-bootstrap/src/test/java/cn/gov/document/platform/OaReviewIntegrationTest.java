package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OaReviewIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    @Test
    void oaCanUploadDocxAndReadItsOwnResultWithoutCreatingDuplicates() throws Exception {
        byte[] docx = docx("关于开展服务检查的请示", "请批准开展服务检查。", "截至目前已完成准备工作。");
        String tenant = "tenant-oa-upload";
        String businessId = "OA-REVIEW-001";
        String body = mockMvc.perform(multipart("/api/v1/integrations/oa/review-tasks/upload")
                        .file(file(docx))
                        .param("businessId", businessId)
                        .param("documentTypeCode", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "oa-system")
                        .header("Idempotency-Key", "oa-review-upload-001"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode created = json.readTree(body).path("data");
        String id = created.path("reviewTask").path("id").asText();
        assertThat(id).isNotBlank();
        assertThat(created.path("reviewTask").path("sourceSystem").asText()).isEqualTo("OA");
        assertThat(created.path("reviewTask").path("documentFileId").asText()).contains("/review-input/");
        assertThat(created.path("statusUrl").asText()).endsWith("/" + id);

        mockMvc.perform(multipart("/api/v1/integrations/oa/review-tasks/upload")
                        .file(file(docx))
                        .param("businessId", businessId)
                        .param("documentTypeCode", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "oa-system")
                        .header("Idempotency-Key", "oa-review-upload-001"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        mockMvc.perform(multipart("/api/v1/integrations/oa/review-tasks/upload")
                        .file(file(docx("不同标题", "不同正文")))
                        .param("businessId", businessId)
                        .param("documentTypeCode", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("Idempotency-Key", "oa-review-upload-001"))
                .andExpect(status().isConflict());

        mockMvc.perform(multipart("/api/v1/integrations/oa/review-tasks/upload")
                        .file(file(docx))
                        .param("businessId", businessId)
                        .param("documentTypeCode", "请示")
                        .header("X-Tenant-Id", tenant)
                        .header("Idempotency-Key", "oa-review-upload-002"))
                .andExpect(status().isConflict());

        String result = mockMvc.perform(get("/api/v1/integrations/oa/review-tasks/{id}", id)
                        .param("businessId", businessId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json.readTree(result)
                        .path("data")
                        .path("reviewTask")
                        .path("id")
                        .asText())
                .isEqualTo(id);
        String byBusinessId = mockMvc.perform(get("/api/v1/integrations/oa/review-tasks")
                        .param("businessId", businessId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json.readTree(byBusinessId)
                        .path("data")
                        .path("reviewTask")
                        .path("id")
                        .asText())
                .isEqualTo(id);
        assertThat(json.readTree(result).path("data").path("issues").isArray()).isTrue();
        assertThat(json.readTree(result)
                        .path("data")
                        .path("reports")
                        .get(0)
                        .path("reportVersion")
                        .asInt())
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/integrations/oa/review-tasks/{id}/reports/1/export", id)
                        .param("businessId", businessId)
                        .param("format", "DOCX")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/integrations/oa/review-tasks/{id}", id)
                        .param("businessId", "wrong")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/integrations/oa/review-tasks/{id}", id)
                        .param("businessId", businessId)
                        .header("X-Tenant-Id", "other-tenant"))
                .andExpect(status().isNotFound());
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file", "待审请示.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
    }

    private static byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument word = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs)
                word.createParagraph().createRun().setText(paragraph);
            word.write(out);
            return out.toByteArray();
        }
    }
}
