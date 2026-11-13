package cn.gov.document.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "platform.storage.file-root=target/test-knowledge-workflow")
@AutoConfigureMockMvc
class KnowledgeWritingWorkflowIntegrationTest {
    private static final String TENANT = "tenant-knowledge-writing";
    private static final String USER = "writer-evidence";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void onlyVerifiedSelectedExcerptEntersDocumentAndEditRequiresCitationReview() throws Exception {
        JsonNode source = data(mvc.perform(
                        post("/api/v1/knowledge/sources")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sourceCode\":\"EVIDENCE\",\"sourceName\":\"专项资料\",\"category\":\"项目资料\",\"storageType\":\"全文索引\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String sourceId = source.path("id").asText();
        String original = "经核对，专项项目计划于2027年启动。";
        MockMultipartFile file =
                new MockMultipartFile("file", "evidence.txt", "text/plain", original.getBytes(StandardCharsets.UTF_8));
        JsonNode uploaded =
                data(mvc.perform(multipart("/api/v1/knowledge/sources/{sourceId}/documents/upload", sourceId)
                                .file(file)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        String documentId = uploaded.path("id").asText();
        mvc.perform(post("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}/parse", sourceId, documentId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk());
        JsonNode chunks = data(mvc.perform(
                        get("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}/chunks", sourceId, documentId)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String chunkId = chunks.get(0).path("id").asText();
        JsonNode task = data(mvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"taskName\":\"项目请示\",\"documentTypeCode\":\"请示\",\"documentCategory\":\"法定公文\",\"writingTopic\":\"申请专项项目\",\"writingPurpose\":\"申请立项\",\"expectedLengthMin\":500,\"expectedLengthMax\":1000,\"securityLevel\":\"内部\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String taskId = task.path("id").asText();
        mvc.perform(post("/api/v1/writing-tasks/{taskId}/materials/from-knowledge", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("sourceId", sourceId)
                                .put("documentId", documentId)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageMode").value("全文参考"))
                .andExpect(jsonPath("$.data.permissionStatus").value("仅供参考"));
        mvc.perform(post("/api/v1/writing-tasks/{taskId}/materials/from-knowledge", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("sourceId", sourceId)
                                .put("documentId", documentId)
                                .put("chunkId", chunkId)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageMode").value("指定片段"))
                .andExpect(jsonPath("$.data.permissionStatus").value("仅供参考"));
        JsonNode outline = data(mvc.perform(post("/api/v1/writing-tasks/{taskId}/outlines", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        int outlineVersion = outline.path("version").asInt();
        String nodeId = outline.path("nodes").get(0).path("id").asText();
        mvc.perform(post("/api/v1/writing-tasks/{taskId}/outlines/{version}/confirm", taskId, outlineVersion)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk());
        String selection = json.createObjectNode()
                .put("outlineNodeId", nodeId)
                .put("sourceId", sourceId)
                .put("documentId", documentId)
                .put("chunkId", chunkId)
                .put("usageType", "事实依据")
                .put("selectionReason", "本任务项目启动时间")
                .put("applicabilityConfirmed", true)
                .toString();
        mvc.perform(post("/api/v1/writing-tasks/{taskId}/knowledge-selections", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection))
                .andExpect(status().isUnprocessableEntity());
        JsonNode details = data(mvc.perform(get("/api/v1/knowledge/sources/{sourceId}/documents", sourceId)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0);
        mvc.perform(put("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}", sourceId, documentId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("documentTitle", "项目启动依据")
                                .put("metadata", "{}")
                                .put("authorityLevel", "单位制度")
                                .put("permissionLevel", "内部")
                                .put("usageType", "事实依据")
                                .put("completeness", "本地原文可用")
                                .put("verificationStatus", "已核验")
                                .put("validityStatus", "不适用")
                                .put("lockVersion", details.path("lockVersion").asInt())
                                .toString()))
                .andExpect(status().isOk());
        JsonNode selected = data(mvc.perform(post("/api/v1/writing-tasks/{taskId}/knowledge-selections", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode inserted =
                data(mvc.perform(post("/api/v1/writing-tasks/{taskId}/document/knowledge-insertions", taskId)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.createObjectNode()
                                        .put("selectionId", selected.path("id").asText())
                                        .put("mode", "原文摘录")
                                        .put("text", original)
                                        .put("confirmed", true)
                                        .toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.paragraphs[0].plainText").value(original))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode paragraph = inserted.path("paragraphs").get(0);
        mvc.perform(get("/api/v1/writing-tasks/{taskId}/document/citations", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reviewStatus").value("已引用"));
        mvc.perform(put(
                                "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
                                taskId,
                                paragraph.path("id").asText())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("plainText", original + "人工补充。")
                                .put(
                                        "lockVersion",
                                        paragraph.path("lockVersion").asInt())
                                .toString()))
                .andExpect(status().isOk());
        String pendingBody = mvc.perform(get("/api/v1/writing-tasks/{taskId}/document/citations", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reviewStatus").value("待复核"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String citationId = data(pendingBody).get(0).path("id").asText();
        mvc.perform(post(
                                "/api/v1/writing-tasks/{taskId}/document/citations/{citationId}/confirm-knowledge",
                                taskId,
                                citationId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("citedFragment", original)
                                .put("sourceExcerpt", original)
                                .put("positionStart", 0)
                                .put("positionEnd", original.length())
                                .put("confirmed", true)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("已引用"));
        JsonNode currentSource = data(mvc.perform(get("/api/v1/knowledge/sources/{sourceId}/documents", sourceId)
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", USER))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0);
        mvc.perform(put("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}", sourceId, documentId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("documentTitle", "项目启动依据")
                                .put("metadata", "{}")
                                .put("authorityLevel", "单位制度")
                                .put("permissionLevel", "内部")
                                .put("usageType", "事实依据")
                                .put("completeness", "本地原文可用")
                                .put("verificationStatus", "已核验")
                                .put("validityStatus", "已失效")
                                .put(
                                        "lockVersion",
                                        currentSource.path("lockVersion").asInt())
                                .toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/writing-tasks/{taskId}/document/citations", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reviewStatus").value("待复核"));
    }

    private JsonNode data(String body) throws Exception {
        return json.readTree(body).path("data");
    }
}
