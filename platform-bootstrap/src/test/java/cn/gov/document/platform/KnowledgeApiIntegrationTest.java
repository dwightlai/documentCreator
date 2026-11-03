package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "platform.storage.file-root=target/test-file-storage")
@AutoConfigureMockMvc
class KnowledgeApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void categoryRequiresExplicitReclassificationAndIndexRemainsReferenceOnly() throws Exception {
        String tenant = "tenant-evidence-boundary";
        String created = mockMvc.perform(post("/api/v1/knowledge/sources")
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"sourceCode\":\"EVIDENCE-KB\",\"sourceName\":\"业务依据\",\"category\":\"财政业务\",\"storageType\":\"全文索引\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();
        int version = objectMapper.readTree(created).path("data").path("lockVersion").asInt();
        mockMvc.perform(put("/api/v1/knowledge/sources/{id}", id)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"sourceName\":\"业务依据\",\"category\":\"历史公文\",\"storageType\":\"全文索引\",\"lockVersion\":" + version + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/knowledge/sources/{id}/reclassify", id)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"category\":\"历史公文\",\"lockVersion\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("历史公文"));
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "可核对的本地正文。".getBytes(StandardCharsets.UTF_8));
        String uploaded = mockMvc.perform(multipart("/api/v1/knowledge/sources/{id}/documents/upload", id)
                        .file(file).header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verificationStatus").value("待核验"))
                .andReturn().getResponse().getContentAsString();
        String documentId = objectMapper.readTree(uploaded).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/knowledge/sources/{id}/documents/{documentId}/parse", id, documentId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/knowledge/sources/{id}/documents/{documentId}/read", id, documentId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "writer-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.chunks[0].chunkText").value("可核对的本地正文。"));
    }

    @Test
    void editsAndDeletesKnowledgeSource() throws Exception {
        String tenant = "tenant-source-crud";
        String created = mockMvc.perform(post("/api/v1/knowledge/sources")
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"sourceCode\":\"SOURCE-CRUD\",\"sourceName\":\"原知识库\",\"category\":\"单位制度\",\"storageType\":\"全文索引\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();
        int lockVersion = objectMapper.readTree(created).path("data").path("lockVersion").asInt();
        mockMvc.perform(put("/api/v1/knowledge/sources/{id}", id)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"sourceName\":\"新知识库\",\"category\":\"单位制度\",\"storageType\":\"全文索引\",\"lockVersion\":" + lockVersion + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceName").value("新知识库"));
        mockMvc.perform(delete("/api/v1/knowledge/sources/{id}", id)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/knowledge/sources").header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void editsMetadataAndChunksThenDeletesDocument() throws Exception {
        String tenant = "tenant-knowledge-edit";
        String source = mockMvc.perform(post("/api/v1/knowledge/sources")
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"sourceCode\":\"EDIT-KB\",\"sourceName\":\"编辑测试库\",\"category\":\"单位制度\",\"storageType\":\"全文索引\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String sourceId = objectMapper.readTree(source).path("data").path("id").asText();
        MockMultipartFile file = new MockMultipartFile("file", "edit.txt", "text/plain",
                "原始知识内容。".getBytes(StandardCharsets.UTF_8));
        String document = mockMvc.perform(multipart("/api/v1/knowledge/sources/{s}/documents/upload", sourceId)
                        .file(file).header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String documentId = objectMapper.readTree(document).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/knowledge/sources/{s}/documents/{d}/parse", sourceId, documentId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk());
        String details = mockMvc.perform(get("/api/v1/knowledge/sources/{s}/documents", sourceId)
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int documentLock = objectMapper.readTree(details).path("data").get(0).path("lockVersion").asInt();
        mockMvc.perform(put("/api/v1/knowledge/sources/{s}/documents/{d}", sourceId, documentId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"documentTitle\":\"更新后的标题\",\"metadata\":\"{}\",\"authorityLevel\":\"单位制度\",\"permissionLevel\":\"内部\",\"lockVersion\":" + documentLock + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.documentTitle").value("更新后的标题"));
        String chunks = mockMvc.perform(get("/api/v1/knowledge/sources/{s}/documents/{d}/chunks", sourceId, documentId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String chunkId = objectMapper.readTree(chunks).path("data").get(0).path("id").asText();
        int chunkLock = objectMapper.readTree(chunks).path("data").get(0).path("lockVersion").asInt();
        mockMvc.perform(put("/api/v1/knowledge/sources/{s}/documents/{d}/chunks/{c}", sourceId, documentId, chunkId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1")
                        .contentType("application/json")
                        .content("{\"chunkText\":\"人工修订知识内容\",\"lockVersion\":" + chunkLock + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.chunkText").value("人工修订知识内容"));
        mockMvc.perform(get("/api/v1/knowledge/search").param("q", "人工修订")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].snippet").value("人工修订知识内容"));
        mockMvc.perform(delete("/api/v1/knowledge/sources/{s}/documents/{d}", sourceId, documentId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/knowledge/sources/{s}/documents", sourceId).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/knowledge/search").param("q", "人工修订")
                        .header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void uploadsChunksSearchesAndFiltersBeforeRetrieval() throws Exception {
        String tenant = "tenant-knowledge";
        String sourceBody = mockMvc.perform(
                        post("/api/v1/knowledge/sources")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin-1")
                                .contentType("application/json")
                                .content(
                                        "{\"sourceCode\":\"POLICY\",\"sourceName\":\"政策制度库\",\"category\":\"单位制度\",\"storageType\":\"全文索引\",\"permissionScope\":\"本单位\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sourceId =
                objectMapper.readTree(sourceBody).path("data").path("id").asText();
        String publicText = "专项建设项目资金应当执行预算管理制度。项目申请材料应包含资金测算、绩效目标和风险控制措施。".repeat(14);
        MockMultipartFile publicFile =
                new MockMultipartFile("file", "policy.txt", "text/plain", publicText.getBytes(StandardCharsets.UTF_8));
        String documentBody = mockMvc.perform(
                        multipart("/api/v1/knowledge/sources/{sourceId}/documents/upload", sourceId)
                                .file(publicFile)
                                .param("title", "专项资金管理制度")
                                .param("authorityLevel", "单位制度")
                                .param("permissionLevel", "内部")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedStatus").value("待解析"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId =
                objectMapper.readTree(documentBody).path("data").path("id").asText();
        String originalPath = objectMapper
                .readTree(documentBody)
                .path("data")
                .path("originalFilePath")
                .asText();
        assertThat(originalPath).doesNotStartWith("extracted://").contains("/knowledge/");
        assertThat(Files.readString(
                        Path.of("target", "test-file-storage").resolve(originalPath), StandardCharsets.UTF_8))
                .isEqualTo(publicText);
        mockMvc.perform(post("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}/parse", sourceId, documentId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已完成"))
                .andExpect(jsonPath("$.data.chunkCount").value(org.hamcrest.Matchers.greaterThan(1)));
        mockMvc.perform(get("/api/v1/knowledge/search").param("q", "资金测算").header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentTitle").value("专项资金管理制度"))
                .andExpect(jsonPath("$.data[0].fileHash").isNotEmpty());
        String writingBody = mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "writer-1")
                                .contentType("application/json")
                                .content(
                                        "{\"taskName\":\"资金制度问答\",\"documentTypeCode\":\"报告\",\"documentCategory\":\"法定公文\",\"writingTopic\":\"专项资金管理\",\"writingPurpose\":\"汇报资金管理情况\",\"expectedLengthMin\":500,\"expectedLengthMax\":1000,\"securityLevel\":\"内部\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String writingId =
                objectMapper.readTree(writingBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/qa", writingId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "writer-1")
                        .contentType("application/json")
                        .content("{\"question\":\"资金测算\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerType").value("原文摘录汇总"))
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value("专项资金管理制度"));

        MockMultipartFile secretFile = new MockMultipartFile(
                "file", "secret.txt", "text/plain", "绝密安排仅限专项工作组知悉。".getBytes(StandardCharsets.UTF_8));
        String secretBody = mockMvc.perform(multipart("/api/v1/knowledge/sources/{sourceId}/documents/upload", sourceId)
                        .file(secretFile)
                        .param("title", "专项工作安排")
                        .param("permissionLevel", "机密")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secretId =
                objectMapper.readTree(secretBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}/parse", sourceId, secretId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/knowledge/search")
                        .param("q", "绝密安排")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("tenant_id", tenant))
                                .authorities(new SimpleGrantedAuthority("ROLE_platform.user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/knowledge/search")
                        .param("q", "绝密安排")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/knowledge/sources").header("X-Tenant-Id", "other-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
