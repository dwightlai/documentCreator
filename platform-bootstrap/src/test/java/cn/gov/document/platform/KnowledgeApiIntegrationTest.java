package cn.gov.document.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test") @SpringBootTest(properties="platform.storage.file-root=target/test-file-storage") @AutoConfigureMockMvc
class KnowledgeApiIntegrationTest {
    @Autowired MockMvc mockMvc;@Autowired ObjectMapper objectMapper;
    @Test void uploadsChunksSearchesAndFiltersBeforeRetrieval()throws Exception{
        String tenant="tenant-knowledge";
        String sourceBody=mockMvc.perform(post("/api/v1/knowledge/sources").header("X-Tenant-Id",tenant).header("X-User-Id","admin-1")
                .contentType("application/json").content("{\"sourceCode\":\"POLICY\",\"sourceName\":\"政策制度库\",\"category\":\"单位制度\",\"storageType\":\"全文索引\",\"permissionScope\":\"本单位\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String sourceId=objectMapper.readTree(sourceBody).path("data").path("id").asText();
        String publicText="专项建设项目资金应当执行预算管理制度。项目申请材料应包含资金测算、绩效目标和风险控制措施。".repeat(14);
        MockMultipartFile publicFile=new MockMultipartFile("file","policy.txt","text/plain",publicText.getBytes(StandardCharsets.UTF_8));
        String documentBody=mockMvc.perform(multipart("/api/v1/knowledge/sources/{sourceId}/documents/upload",sourceId).file(publicFile)
                .param("title","专项资金管理制度").param("authorityLevel","单位制度").param("permissionLevel","内部")
                .header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.parsedStatus").value("待解析")).andReturn().getResponse().getContentAsString();
        String documentId=objectMapper.readTree(documentBody).path("data").path("id").asText();
        String originalPath=objectMapper.readTree(documentBody).path("data").path("originalFilePath").asText();
        assertThat(originalPath).doesNotStartWith("extracted://").contains("/knowledge/");
        assertThat(Files.readString(Path.of("target","test-file-storage").resolve(originalPath),StandardCharsets.UTF_8)).isEqualTo(publicText);
        mockMvc.perform(post("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}/parse",sourceId,documentId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("已完成")).andExpect(jsonPath("$.data.chunkCount").value(org.hamcrest.Matchers.greaterThan(1)));
        mockMvc.perform(get("/api/v1/knowledge/search").param("q","资金测算").header("X-Tenant-Id",tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].documentTitle").value("专项资金管理制度"))
            .andExpect(jsonPath("$.data[0].fileHash").isNotEmpty());
        String writingBody=mockMvc.perform(post("/api/v1/writing-tasks").header("X-Tenant-Id",tenant).header("X-User-Id","writer-1")
                .contentType("application/json").content("{\"taskName\":\"资金制度问答\",\"documentTypeCode\":\"报告\",\"documentCategory\":\"法定公文\",\"writingTopic\":\"专项资金管理\",\"writingPurpose\":\"汇报资金管理情况\",\"expectedLengthMin\":500,\"expectedLengthMax\":1000,\"securityLevel\":\"内部\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String writingId=objectMapper.readTree(writingBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/qa",writingId).header("X-Tenant-Id",tenant).header("X-User-Id","writer-1")
                .contentType("application/json").content("{\"question\":\"资金测算\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answerType").value("原文摘录汇总"))
            .andExpect(jsonPath("$.data.sources[0].documentTitle").value("专项资金管理制度"));

        MockMultipartFile secretFile=new MockMultipartFile("file","secret.txt","text/plain","绝密安排仅限专项工作组知悉。".getBytes(StandardCharsets.UTF_8));
        String secretBody=mockMvc.perform(multipart("/api/v1/knowledge/sources/{sourceId}/documents/upload",sourceId).file(secretFile)
                .param("title","专项工作安排").param("permissionLevel","机密").header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secretId=objectMapper.readTree(secretBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/knowledge/sources/{sourceId}/documents/{documentId}/parse",sourceId,secretId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/knowledge/search").param("q","绝密安排")
                .with(jwt().jwt(token->token.subject("user-1").claim("tenant_id",tenant)).authorities(new SimpleGrantedAuthority("ROLE_platform.user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/knowledge/search").param("q","绝密安排").header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/knowledge/sources").header("X-Tenant-Id","other-tenant"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
}
