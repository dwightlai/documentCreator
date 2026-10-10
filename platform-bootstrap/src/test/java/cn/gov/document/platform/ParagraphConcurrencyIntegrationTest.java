package cn.gov.document.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ParagraphConcurrencyIntegrationTest {
    private static final String TENANT = "tenant-paragraph-concurrency";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void editLeaseBlocksOtherUsersAndStaleSavesRebaseOrConflict() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "alice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"并发编辑请示","documentTypeCode":"请示","documentCategory":"法定公文",
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
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/writing-tasks/{taskId}/generate/section", taskId)
                            .header("X-Tenant-Id", TENANT)
                            .header("X-User-Id", "alice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"outlineNodeId\":\""
                                    + outline.path("nodes").get(i).path("id").asText() + "\"}"))
                    .andExpect(status().isOk());
        }
        JsonNode document =
                data(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/writing-tasks/{taskId}/document", taskId)
                        .header("X-Tenant-Id", TENANT)));
        JsonNode first = document.path("paragraphs").get(0);
        JsonNode second = document.path("paragraphs").get(1);

        // alice 持有第一段租约：bob 不能获取租约，也不能保存该段
        lease(taskId, first, "alice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holderId").value("alice"));
        lease(taskId, first, "bob").andExpect(status().isConflict());
        save(taskId, first, "bob", "bob 的改写").andExpect(status().isConflict());

        // bob 基于旧版本编辑另一段：该段未被他人修改，变基到最新版本后保存成功
        save(taskId, first, "alice", "alice 的改写").andExpect(status().isOk());
        save(taskId, second, "bob", "bob 改写第二段")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphs[0].plainText").value("alice 的改写"))
                .andExpect(jsonPath("$.data.paragraphs[1].plainText").value("bob 改写第二段"));

        // alice 释放租约后，bob 仍基于旧内容保存第一段：内容已被修改，必须冲突而不是静默覆盖
        mockMvc.perform(delete(
                                "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}/edit-lease",
                                taskId,
                                first.path("id").asText())
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "alice"))
                .andExpect(status().isOk());
        save(taskId, first, "bob", "bob 基于旧内容的改写").andExpect(status().isConflict());
    }

    private ResultActions lease(String taskId, JsonNode paragraph, String user) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}/edit-lease",
                        taskId,
                        paragraph.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", user));
    }

    private ResultActions save(String taskId, JsonNode paragraph, String user, String text) throws Exception {
        return mockMvc.perform(put(
                        "/api/v1/writing-tasks/{taskId}/document/paragraphs/{paragraphId}",
                        taskId,
                        paragraph.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "plainText",
                        text,
                        "lockVersion",
                        paragraph.path("lockVersion").asInt()))));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
