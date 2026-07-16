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
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DocumentGenerationStreamIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void streamsSectionAndPersistsStreamingGenerationRecord() throws Exception {
        String tenant = "tenant-stream";
        String taskBody = mockMvc.perform(post("/api/v1/writing-tasks").header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "stream-writer").contentType(MediaType.APPLICATION_JSON).content("""
                        {"taskName":"流式生成请示","documentTypeCode":"请示","documentCategory":"法定公文",
                         "writingTopic":"申请开展试点","writingPurpose":"向上级申请批准开展试点",
                         "expectedLengthMin":800,"expectedLengthMax":1600,"securityLevel":"内部"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskBody).path("data").path("id").asText();
        String outlineBody = mockMvc.perform(post("/api/v1/writing-tasks/{id}/outlines", taskId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "stream-writer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode outline = objectMapper.readTree(outlineBody).path("data");
        int version = outline.path("version").asInt();
        String nodeId = outline.path("nodes").get(0).path("id").asText();
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/outlines/{version}/confirm", taskId, version)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "stream-writer"))
                .andExpect(status().isOk());

        MvcResult stream = mockMvc.perform(post("/api/v1/writing-tasks/{id}/generate/section/stream", taskId)
                        .header("X-Tenant-Id", tenant).header("X-User-Id", "stream-writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outlineNodeId\":\"" + nodeId + "\",\"instruction\":\"突出试点必要性\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        stream.getAsyncResult(30_000);
        mockMvc.perform(asyncDispatch(stream)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:completed")));
        mockMvc.perform(get("/api/v1/writing-tasks/{id}/generation-records", taskId).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].streaming").value(true))
                .andExpect(jsonPath("$.data[0].materialSnapshotVersion").value(0));
        mockMvc.perform(get("/api/v1/writing-tasks/{id}/document", taskId).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.paragraphs[0].outlineNodeId").value(nodeId));
    }
}
