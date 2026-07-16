package cn.gov.document.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class WritingTaskApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsListsAndIsolatesWritingTasksByTenant() throws Exception {
        String body = """
            {"taskName":"专项资金请示","documentTypeCode":"请示","documentCategory":"法定公文",
             "writingTopic":"申请专项建设资金","writingPurpose":"向上级申请批准专项资金",
             "expectedLengthMin":1200,"expectedLengthMax":2400,"securityLevel":"内部"}
            """;
        String response = mockMvc.perform(post("/api/v1/writing-tasks")
                .header("X-Tenant-Id", "tenant-a").header("X-User-Id", "user-a")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("草稿"))
            .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response).path("data");
        assertThat(created.path("id").asText()).hasSize(32);

        mockMvc.perform(get("/api/v1/writing-tasks").header("X-Tenant-Id", "tenant-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/writing-tasks").header("X-Tenant-Id", "tenant-b"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/api/v1/writing-tasks/{id}/recommend-type", created.path("id").asText())
                .header("X-Tenant-Id", "tenant-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].documentType").value("请示"));

        MockMultipartFile material = new MockMultipartFile("file", "政策依据.txt", "text/plain",
                "专项建设资金应当经过测算论证，并建立全过程绩效管理。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/writing-tasks/{id}/materials/upload", created.path("id").asText())
                        .file(material).param("authorityLevel", "正式公文").param("usageMode", "全文参考")
                        .param("citationPurpose", "政策依据").header("X-Tenant-Id", "tenant-a").header("X-User-Id", "user-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.materialName").value("政策依据.txt"))
                .andExpect(jsonPath("$.data.fileHash").isNotEmpty()).andExpect(jsonPath("$.data.filePath").isNotEmpty())
                .andExpect(jsonPath("$.data.selectedFragment").value("专项建设资金应当经过测算论证，并建立全过程绩效管理。"));
        mockMvc.perform(get("/api/v1/writing-tasks/{id}/materials", created.path("id").asText())
                        .header("X-Tenant-Id", "tenant-b"))
                .andExpect(status().isNotFound());

        String ideaBody=mockMvc.perform(post("/api/v1/writing-tasks/{id}/ideas",created.path("id").asText())
                .header("X-Tenant-Id","tenant-a").header("X-User-Id","user-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
            .andExpect(jsonPath("$.data.coreViewpoints.length()").value(3))
            .andExpect(jsonPath("$.data.confirmed").value(false))
            .andReturn().getResponse().getContentAsString();
        JsonNode idea=objectMapper.readTree(ideaBody).path("data");
        mockMvc.perform(put("/api/v1/writing-tasks/{id}/ideas/1",created.path("id").asText())
                .header("X-Tenant-Id","tenant-a").header("X-User-Id","planner-a")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"mainTheme":"围绕专项建设资金形成单一请示事项","coreViewpoints":["依据充分","请求明确"],
                     "writingSequence":["说明背景","提出请求","明确保障"],"mustExpressFacts":"资金测算和政策依据",
                     "optionalContent":"实施成效","missingMaterials":"补充测算表","riskWarnings":"不得虚构审批结论",
                     "confirmed":true,"lockVersion":%d}
                    """.formatted(idea.path("lockVersion").asInt())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmed").value(true))
            .andExpect(jsonPath("$.data.confirmedBy").value("planner-a"));
        mockMvc.perform(put("/api/v1/writing-tasks/{id}/ideas/1",created.path("id").asText())
                .header("X-Tenant-Id","tenant-a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mainTheme\":\"再次修改\",\"coreViewpoints\":[\"观点\"],\"writingSequence\":[\"顺序\"],\"confirmed\":false,\"lockVersion\":1}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/title-candidates",created.path("id").asText())
                .header("X-Tenant-Id","tenant-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].includesSubject").value(true))
            .andExpect(jsonPath("$.data[0].includesDocumentType").value(true));
        mockMvc.perform(get("/api/v1/writing-tasks/{id}/ideas/1",created.path("id").asText())
                .header("X-Tenant-Id","tenant-b"))
            .andExpect(status().isNotFound());
    }
}
