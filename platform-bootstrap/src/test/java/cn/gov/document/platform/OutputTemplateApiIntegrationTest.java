package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "platform.storage.file-root=target/test-file-storage")
@AutoConfigureMockMvc
class OutputTemplateApiIntegrationTest {
    private static final String TENANT = "tenant-template";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void managesTemplateVersionTestPublishRenderAndTenantIsolation() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                """
                {"templateCode":"REQUEST-UP","templateName":"标准请示模板","documentTypeCode":"请示",
                 "hasRedHeader":true,"hasFooterMark":true,"direction":"上行","description":"测试模板"}
                """
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "request-template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                template());

        String created = mockMvc.perform(multipart("/api/v1/output-templates")
                        .file(metadata)
                        .file(file)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "template-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("草稿"))
                .andExpect(jsonPath("$.data.placeholderMap['{title}']").value("公文标题"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String templateId =
                objectMapper.readTree(created).path("data").path("id").asText();

        MockMultipartFile deviating = new MockMultipartFile(
                "file",
                "deviating.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                deviatingDocument());
        String formatReview = mockMvc.perform(multipart("/api/v1/output-templates/{id}/format-review", templateId)
                        .file(deviating)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviationCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var dimensionCodes = objectMapper
                .readTree(formatReview)
                .path("data")
                .path("deviations")
                .valueStream()
                .map(value -> value.path("dimensionCode").asText())
                .toList();
        assertThat(dimensionCodes).containsExactly("PAGE_SIZE", "PLACEHOLDER_RESIDUE");

        mockMvc.perform(post("/api/v1/output-templates/{id}/publish", templateId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "template-admin"))
                .andExpect(status().isUnprocessableEntity());

        byte[] preview = mockMvc.perform(
                        post("/api/v1/output-templates/{id}/test", templateId)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "template-admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"values":{"title":"关于建设政务平台的请示","recipient":"省人民政府：",
                                "content":"现申请建设统一平台。","date":"2026年7月15日"},"sections":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(preview))) {
            String text = document.getParagraphs().stream()
                    .map(value -> value.getText())
                    .reduce("", (left, right) -> left + right);
            assertThat(text).contains("关于建设政务平台的请示", "现申请建设统一平台。").doesNotContain("{title}");
        }

        mockMvc.perform(post("/api/v1/output-templates/{id}/publish", templateId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "template-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("生效"));
        mockMvc.perform(get("/api/v1/output-templates/by-document-type/{type}", "请示")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "writer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/output-templates/{id}/versions", templateId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "writer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lifecycleStatus").value("生效"))
                .andExpect(jsonPath("$.data[0].approvedBy").value("template-admin"));
        String writingTaskBody = mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "writer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"taskName":"模板自动匹配任务","documentTypeCode":"请示","documentCategory":"法定公文",
                                "writingTopic":"申请建设统一政务平台","expectedLengthMin":500,"expectedLengthMax":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outputTemplateId").value(templateId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String writingTaskId =
                objectMapper.readTree(writingTaskBody).path("data").path("id").asText();

        byte[] reviewDocumentBytes = deviatingDocument();
        MockMultipartFile reviewDocument = new MockMultipartFile(
                "file",
                "review-document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                reviewDocumentBytes);
        String reviewBody = mockMvc.perform(multipart("/api/v1/review-tasks/document")
                        .file(reviewDocument)
                        .param("writingTaskId", writingTaskId)
                        .param("outputTemplateId", templateId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outputTemplateId").value(templateId))
                .andExpect(jsonPath("$.data.documentFileHash").isNotEmpty())
                // 含模板偏差 2 项、L4 结构要素 2 项（请示缺签发人、缺版记印发要素）及正文规则问题
                .andExpect(jsonPath("$.data.totalIssues").value(6))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var reviewData = objectMapper.readTree(reviewBody).path("data");
        String reviewTaskId = reviewData.path("id").asText();
        assertThat(reviewData.path("documentFileHash").asText()).hasSize(64);
        assertThat(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("target/test-file-storage")
                        .resolve(reviewData.path("documentFileId").asText())))
                .isEqualTo(reviewDocumentBytes);
        mockMvc.perform(get("/api/v1/review-tasks/{id}/issues", reviewTaskId)
                        .param("category", "格式版式")
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].detectionMethod").value("格式解析-L4"))
                .andExpect(jsonPath("$.data[0].pageNumber").value(1))
                .andExpect(jsonPath("$.data[0].paragraphNumber").value(1));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/review-tasks/{id}/issues/{number}/accept", reviewTaskId, "ISS-0002")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "reviewer"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/review-tasks/{id}", reviewTaskId).header("X-Tenant-Id", "other-template-tenant"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/writing-tasks")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"taskName":"错误模板任务","documentTypeCode":"通知","documentCategory":"法定公文",
                                "writingTopic":"发布工作通知","expectedLengthMin":500,"expectedLengthMax":1000,
                                "outputTemplateId":"%s"}
                                """
                                        .formatted(templateId)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/output-templates")
                        .header("X-Tenant-Id", "other-template-tenant")
                        .header("X-User-Id", "other-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private byte[] template() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var size = section.addNewPgSz();
            size.setW(11906);
            size.setH(16838);
            document.createParagraph().createRun().setText("{title}");
            document.createParagraph().createRun().setText("{recipient}");
            document.createParagraph().createRun().setText("{content}");
            document.createParagraph().createRun().setText("{date}");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] deviatingDocument() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var size = section.addNewPgSz();
            size.setW(10000);
            size.setH(16838);
            document.createParagraph().createRun().setText("{title}");
            document.createParagraph().createRun().setText("省人民政府：");
            document.createParagraph().createRun().setText("现申请建设统一平台。");
            document.createParagraph().createRun().setText("2026年7月15日");
            document.write(output);
            return output.toByteArray();
        }
    }
}
