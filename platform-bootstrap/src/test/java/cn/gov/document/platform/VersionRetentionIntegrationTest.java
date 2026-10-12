package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 每个文稿最多保留 2 个 AI 生成版本、宽限期 0 天，便于在一次运行中验证标记与清理。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"platform.retention.ai-generated-max=2", "platform.retention.purge-grace-days=0"})
@AutoConfigureMockMvc
class VersionRetentionIntegrationTest {
    private static final String TENANT = "tenant-version-retention";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void purgesOldAiVersionsButKeepsCurrentManualAndProtectedOnes() throws Exception {
        String taskId = data(mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "writer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"版本保留样稿","documentTypeCode":"通知","documentCategory":"法定公文",
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
        // V1—V4 为 AI 生成版本
        for (int i = 0; i < 4; i++) {
            data(mockMvc.perform(post("/api/v1/writing-tasks/{id}/generate/section", taskId)
                    .header("X-Tenant-Id", TENANT)
                    .header("X-User-Id", "writer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"outlineNodeId\":\""
                            + outline.path("nodes")
                                    .get(i % outline.path("nodes").size())
                                    .path("id")
                                    .asText() + "\"}")));
        }
        // V5 为人工提交版本（永久保留），当前版本
        JsonNode document = data(mockMvc.perform(
                get("/api/v1/writing-tasks/{id}/document", taskId).header("X-Tenant-Id", TENANT)));
        JsonNode first = document.path("paragraphs").get(0);
        data(mockMvc.perform(put(
                        "/api/v1/writing-tasks/{id}/document/paragraphs/{pid}",
                        taskId,
                        first.path("id").asText())
                .header("X-Tenant-Id", TENANT)
                .header("X-User-Id", "writer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "plainText",
                        "人工修改。",
                        "lockVersion",
                        first.path("lockVersion").asInt())))));
        // 管理员保护 V1
        mockMvc.perform(put("/api/v1/writing-tasks/{id}/document/versions/1/protection", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"protectedVersion\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protectedVersion").value(true));

        // 最新 2 个 AI 版本（V4、V3）保留；V2 超出数量被清理；V1 受保护
        JsonNode result = data(
                mockMvc.perform(post("/api/v1/document-version-retention/run").header("X-Tenant-Id", TENANT)));
        assertThat(result.path("marked").asInt()).isEqualTo(1);
        assertThat(result.path("purged").asInt()).isEqualTo(1);

        Map<Integer, JsonNode> versions = new HashMap<>();
        data(mockMvc.perform(get("/api/v1/writing-tasks/{id}/document/versions", taskId)
                        .header("X-Tenant-Id", TENANT)))
                .forEach(v -> versions.put(v.path("versionNumber").asInt(), v));
        assertThat(versions.get(2).path("contentPurged").asBoolean()).isTrue();
        assertThat(versions.get(1).path("contentPurged").asBoolean()).isFalse();
        assertThat(versions.get(3).path("contentPurged").asBoolean()).isFalse();
        assertThat(versions.get(5).path("contentPurged").asBoolean()).isFalse();

        mockMvc.perform(get("/api/v1/writing-tasks/{id}/document/versions/2", taskId)
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/writing-tasks/{id}/document/versions/2/restore", taskId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "writer"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/writing-tasks/{id}/document/versions/1", taskId)
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk());

        // 再次运行不会重复清理
        JsonNode again = data(
                mockMvc.perform(post("/api/v1/document-version-retention/run").header("X-Tenant-Id", TENANT)));
        assertThat(again.path("marked").asInt() + again.path("purged").asInt()).isZero();
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String body =
                actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
