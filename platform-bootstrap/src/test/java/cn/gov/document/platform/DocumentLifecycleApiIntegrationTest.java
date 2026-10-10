package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DocumentLifecycleApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void confirmsOutlineGeneratesVersionsEditsAndExportsDocx() throws Exception {
        String tenant = "tenant-document";
        String createdBody = mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "editor-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"专项建设资金请示","documentTypeCode":"请示","documentCategory":"法定公文",
                     "writingTopic":"申请专项建设资金","writingPurpose":"向上级机关申请批准专项资金",
                     "expectedLengthMin":1200,"expectedLengthMax":2400,"securityLevel":"内部"}
                    """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId =
                objectMapper.readTree(createdBody).path("data").path("id").asText();
        String materialBody = mockMvc.perform(
                        post("/api/v1/writing-tasks/{taskId}/materials", taskId)
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "editor-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"materialName":"项目论证报告","sourceType":"上传","authorityLevel":"正式公文",
                     "usageMode":"指定片段","citationPurpose":"事实依据","permissionStatus":"允许",
                     "selectedFragment":"截止目前为止，项目建设具有现实必要性。","locked":true}
                    """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String materialId =
                objectMapper.readTree(materialBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/materials/snapshot", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotVersion").value(1))
                .andExpect(jsonPath("$.data.allowedCount").value(1))
                .andExpect(jsonPath("$.data.materials[0].materialId").value(materialId));
        mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/materials/snapshot/1", taskId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials.length()").value(1));

        String outlineBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/outlines", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(4))
                .andExpect(jsonPath("$.data.nodes[0].title").value("请示背景与依据"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode outline = objectMapper.readTree(outlineBody).path("data");
        int outlineVersion = outline.path("version").asInt();
        String nodeId = outline.path("nodes").get(0).path("id").asText();

        String addedOutline = mockMvc.perform(
                        post("/api/v1/writing-tasks/{taskId}/outlines/{version}/nodes", taskId, outlineVersion)
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "editor-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"order\":2,\"title\":\"补充说明\",\"purpose\":\"补充风险分析\",\"targetWordCount\":200,\"required\":false,\"referencedMaterialIds\":[\""
                                                + materialId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode addedNode = null;
        for (JsonNode item : objectMapper.readTree(addedOutline).path("data").path("nodes")) {
            if ("补充说明".equals(item.path("title").asText())) addedNode = item;
        }
        assertThat(addedNode).isNotNull();
        String addedNodeId = addedNode.path("id").asText();
        mockMvc.perform(put(
                                "/api/v1/writing-tasks/{taskId}/outlines/{version}/nodes/{nodeId}",
                                taskId,
                                outlineVersion,
                                addedNodeId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"order\":4,\"title\":\"风险保障\",\"purpose\":\"说明风险控制\",\"targetWordCount\":240,\"required\":false,\"referencedMaterialIds\":[],\"lockVersion\":"
                                        + addedNode.path("lockVersion").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[3].title").value("风险保障"));
        mockMvc.perform(post(
                                "/api/v1/writing-tasks/{taskId}/outlines/{version}/nodes/{nodeId}/regenerate",
                                taskId,
                                outlineVersion,
                                addedNodeId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[3].generationStatus").value("待生成"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/writing-tasks/{taskId}/outlines/{version}/nodes/{nodeId}",
                                taskId,
                                outlineVersion,
                                addedNodeId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(4));

        mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/outlines/{version}/confirm", taskId, outlineVersion)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmed").value(true));

        String documentBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/section", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outlineNodeId\":\"" + nodeId + "\",\"instruction\":\"突出项目必要性\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.paragraphs.length()").value(1))
                .andExpect(jsonPath("$.data.paragraphs[0].sourceMaterialIds[0]").value(materialId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode paragraph = objectMapper
                .readTree(documentBody)
                .path("data")
                .path("paragraphs")
                .get(0);

        String editedBody = mockMvc.perform(put(
                                "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
                                taskId,
                                paragraph.path("id").asText())
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plainText\":\"截止目前为止，项目建设具有现实必要性。\",\"lockVersion\":"
                                + paragraph.path("lockVersion").asInt() + ",\"changeDescription\":\"人工核准事实表述\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2))
                .andExpect(jsonPath("$.data.paragraphs[0].contentSourceLevel").value("手工"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String editedParagraphId = objectMapper
                .readTree(editedBody)
                .path("data")
                .path("paragraphs")
                .get(0)
                .path("id")
                .asText();
        String documentId =
                objectMapper.readTree(editedBody).path("data").path("id").asText();
        String commentBody = mockMvc.perform(post("/api/v1/documents/{documentId}/comments", documentId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paragraphId\":\"" + editedParagraphId
                                + "\",\"commentType\":\"意见\",\"commentText\":\"请核实项目数据来源\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String commentId =
                objectMapper.readTree(commentBody).path("data").path("id").asText();
        mockMvc.perform(put("/api/v1/documents/{documentId}/comments/{commentId}/resolve", documentId, commentId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true))
                .andExpect(jsonPath("$.data.resolvedBy").value("editor-1"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/compare", documentId)
                        .param("fromVersion", "1")
                        .param("toVersion", "2")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modified").value(1))
                .andExpect(jsonPath("$.data.paragraphs[0].status").value("修改"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/comments", documentId)
                        .header("X-Tenant-Id", "another-tenant"))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/v1/writing-tasks/{taskId}/document/citations", taskId)
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "editor-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"paragraphId\":\"" + editedParagraphId + "\",\"materialId\":\"" + materialId
                                                + "\",\"citationType\":\"原文摘录\",\"citedFragment\":\"截止目前为止\",\"positionStart\":0,\"positionEnd\":6,\"displayMode\":\"角标\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/citations", taskId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document/versions", taskId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/document", taskId).header("X-Tenant-Id", "another-tenant"))
                .andExpect(status().isNotFound());

        byte[] docx = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/export", taskId)
                        .header("X-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"DOCX\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_TYPE,
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(docx).hasSizeGreaterThan(500);
        assertThat(docx[0]).isEqualTo((byte) 'P');
        assertThat(docx[1]).isEqualTo((byte) 'K');
        byte[] html = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/export", taskId)
                        .header("X-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"HTML\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(html, java.nio.charset.StandardCharsets.UTF_8))
                .contains("<!doctype html>", "截止目前为止", "AI 辅助生成", "[1]");
        byte[] htmlWithoutCitations = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/export", taskId)
                        .header("X-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"HTML\",\"retainCitations\":false}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(htmlWithoutCitations, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("[1] 材料");
        byte[] pdf = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/document/export", taskId)
                        .header("X-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(pdf).hasSizeGreaterThan(10_000).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');

        mockMvc.perform(get("/api/v1/review-plans")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
        String reviewBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/initiate-review", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("审校完成"))
                .andExpect(jsonPath("$.data.reviewPlanId").isNotEmpty())
                .andExpect(jsonPath("$.data.totalIssues").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reviewId =
                objectMapper.readTree(reviewBody).path("data").path("id").asText();
        mockMvc.perform(get("/api/v1/review-tasks/{reviewId}/issues", reviewId).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalText").value("截止目前为止"))
                .andExpect(jsonPath("$.data[0].suggestedFix").value("截至目前"))
                .andExpect(jsonPath("$.data[0].pageNumber").value(1))
                .andExpect(jsonPath("$.data[0].paragraphNumber").value(1))
                .andExpect(jsonPath("$.data[0].charPositionStart").isNumber())
                .andExpect(jsonPath("$.data[0].charPositionEnd").isNumber());
        String falsePositiveBody = mockMvc.perform(post(
                                "/api/v1/review-tasks/{reviewId}/issues/{issueNumber}/mark-false-positive",
                                reviewId,
                                "ISS-0001")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"提交样本用于验证误报审核闭环\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("待审核"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String falsePositiveId =
                objectMapper.readTree(falsePositiveBody).path("data").path("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/false-positives/{id}/review", falsePositiveId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"已拒绝\",\"optimizationSuggestion\":\"该命中确属规范问题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewedBy").value("admin-1"));
        String reviewDictionary = mockMvc.perform(post("/api/v1/dictionaries")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictCode\":\"REVIEW-CLOSED-LOOP\",\"dictName\":\"审校闭环词库\",\"dictType\":\"规范用语\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reviewDictionaryId =
                objectMapper.readTree(reviewDictionary).path("data").path("id").asText();
        mockMvc.perform(post(
                                "/api/v1/review-tasks/{reviewId}/issues/{issueNumber}/add-to-dictionary",
                                reviewId,
                                "ISS-0001")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictionaryId\":\"" + reviewDictionaryId
                                + "\",\"sourceReference\":\"本次人工核稿\",\"usageNote\":\"由审校问题沉淀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termText").value("截止目前为止"))
                .andExpect(jsonPath("$.data.standardForm").value("截至目前"));
        mockMvc.perform(post("/api/v1/review-tasks/{reviewId}/issues/batch-accept", reviewId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueNumbers\":[\"ISS-0001\"],\"note\":\"核实后批量采纳\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].processingStatus").value("已采纳"));
        String acceptedDocument = mockMvc.perform(
                        get("/api/v1/writing-tasks/{taskId}/document", taskId).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(3))
                .andExpect(jsonPath("$.data.paragraphs[0].plainText").value("截至目前，项目建设具有现实必要性。"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        mockMvc.perform(post("/api/v1/review-tasks/{reviewId}/reports", reviewId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportVersion").value(1))
                .andExpect(jsonPath("$.data.adoptedCount").value(1))
                .andExpect(jsonPath("$.data.finalReport").value(true));
        byte[] reviewPdf = mockMvc.perform(post("/api/v1/review-tasks/{reviewId}/reports/1/export", reviewId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(reviewPdf).hasSizeGreaterThan(10_000).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        byte[] reviewDocx = mockMvc.perform(post("/api/v1/review-tasks/{reviewId}/reports/1/export", reviewId)
                        .param("format", "DOCX")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(reviewDocx).hasSizeGreaterThan(500).startsWith((byte) 'P', (byte) 'K');
        mockMvc.perform(post("/api/v1/review-tasks/{reviewId}/re-review", reviewId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "reviewer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIssues").value(0));

        JsonNode currentParagraph = objectMapper
                .readTree(acceptedDocument)
                .path("data")
                .path("paragraphs")
                .get(0);
        String rewriteBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/rewrite", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paragraphId\":\""
                                + currentParagraph.path("id").asText() + "\",\"instruction\":\"增强逻辑\",\"lockVersion\":"
                                + currentParagraph.path("lockVersion").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(4))
                .andExpect(jsonPath("$.data.paragraphs[0].plainText").value("为此，截至目前，项目建设具有现实必要性。"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        currentParagraph = objectMapper
                .readTree(rewriteBody)
                .path("data")
                .path("paragraphs")
                .get(0);
        String compressedBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/compress", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paragraphId\":\""
                                + currentParagraph.path("id").asText() + "\",\"targetWordCount\":20,\"lockVersion\":"
                                + currentParagraph.path("lockVersion").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();
        currentParagraph = objectMapper
                .readTree(compressedBody)
                .path("data")
                .path("paragraphs")
                .get(0);
        assertThat(currentParagraph
                        .path("plainText")
                        .asText()
                        .codePointCount(
                                0, currentParagraph.path("plainText").asText().length()))
                .isLessThanOrEqualTo(21);
        String expandedBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/expand", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paragraphId\":\""
                                + currentParagraph.path("id").asText() + "\",\"targetWordCount\":80,\"lockVersion\":"
                                + currentParagraph.path("lockVersion").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(6))
                .andReturn()
                .getResponse()
                .getContentAsString();
        currentParagraph = objectMapper
                .readTree(expandedBody)
                .path("data")
                .path("paragraphs")
                .get(0);
        String continuedBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/continue", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paragraphId\":\""
                                + currentParagraph.path("id").asText()
                                + "\",\"instruction\":\"补充资金测算\",\"lockVersion\":"
                                + currentParagraph.path("lockVersion").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(7))
                .andExpect(jsonPath("$.data.paragraphs.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper
                        .readTree(continuedBody)
                        .path("data")
                        .path("paragraphs")
                        .get(1)
                        .path("plainText")
                        .asText())
                .contains("资金测算");
        String recordsBody = mockMvc.perform(get("/api/v1/writing-tasks/{taskId}/generation-records", taskId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].generationType").value("续写"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode latestRecord = objectMapper.readTree(recordsBody).path("data").get(0);
        assertThat(latestRecord.path("inputContextHash").asText()).hasSize(64);
        assertThat(latestRecord.path("outputHash").asText()).hasSize(64);
        assertThat(latestRecord.path("materialSnapshotVersion").asInt()).isPositive();
        String qaBody = mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/qa", taskId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"本任务还缺少哪些必要材料\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerType").value("无依据提示"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String qaId = objectMapper.readTree(qaBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/qa/{qaId}/insert", taskId, qaId)
                        .header("X-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/qa/{qaId}/insert", taskId, qaId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "editor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(8))
                .andExpect(jsonPath("$.data.paragraphs[2].contentSourceLevel").value("无来源"));
        String oaRequest = "{\"businessId\":\"OA-2026-001\",\"writingTaskId\":\"" + taskId
                + "\",\"documentFileId\":\"FILE-10001\"}";
        mockMvc.perform(post("/api/v1/integrations/oa/review-tasks")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "oa-adapter")
                        .header("Idempotency-Key", "oa-review-2026-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oaRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(202))
                .andExpect(jsonPath("$.data.reviewTask.sourceSystem").value("OA"))
                .andExpect(jsonPath("$.data.reviewTask.businessId").value("OA-2026-001"))
                .andExpect(jsonPath("$.data.reviewTask.documentFileId").value("FILE-10001"));
        mockMvc.perform(post("/api/v1/integrations/oa/review-tasks")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "oa-adapter")
                        .header("Idempotency-Key", "oa-review-2026-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oaRequest))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }
}
