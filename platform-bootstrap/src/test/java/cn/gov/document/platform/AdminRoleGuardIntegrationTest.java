package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminRoleGuardIntegrationTest {
    private static final String TENANT = "tenant-role-guard";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void governanceEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(put("/api/v1/submission-gate")
                        .with(user("platform.user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gate("null")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/document-version-retention/run").with(user("platform.user")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/writing-tasks/any/document/versions/1/protection")
                        .with(user("platform.user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"protectedVersion\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submissionCanRequireAConfirmingRoleAndTheRoleCanBeCleared() throws Exception {
        mockMvc.perform(put("/api/v1/submission-gate")
                        .with(user("platform.admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gate("\"platform.reviewer\"")))
                .andExpect(status().isOk());
        String taskId = objectMapper
                .readTree(mockMvc.perform(
                                post("/api/v1/writing-tasks")
                                        .with(user("platform.user"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                        {"taskName":"角色门禁","documentTypeCode":"通知","documentCategory":"法定公文",
                         "writingTopic":"开展检查","expectedLengthMin":500,"expectedLengthMax":1000}
                        """))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("id")
                .asText();
        assertThat(roleRule(taskId, "platform.user").path("passed").asBoolean()).isFalse();
        assertThat(roleRule(taskId, "platform.reviewer").path("passed").asBoolean())
                .isTrue();

        mockMvc.perform(put("/api/v1/submission-gate")
                        .with(user("platform.admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gate("null")))
                .andExpect(status().isOk());
        JsonNode config = objectMapper
                .readTree(mockMvc.perform(get("/api/v1/submission-gate").with(user("platform.user")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data");
        assertThat(config.path("requiredSubmitRole").isMissingNode()
                        || config.path("requiredSubmitRole").isNull())
                .isTrue();
    }

    private JsonNode roleRule(String taskId, String role) throws Exception {
        // 文稿尚未生成时门禁只返回 DOCUMENT 规则，这里关注角色规则的计算，因此直接读取配置并以对应身份调用检查
        String body = mockMvc.perform(get("/api/v1/writing-tasks/{id}/submission-check", taskId)
                        .with(user(role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (JsonNode rule : objectMapper.readTree(body).path("data").path("rules")) {
            if ("SUBMIT_ROLE".equals(rule.path("code").asText())) return rule;
        }
        throw new AssertionError("缺少 SUBMIT_ROLE 规则：" + body);
    }

    private static String gate(String role) {
        return """
                {"requireReview":true,"requireLatestReview":true,"blockOnSevere":true,
                 "requireImportantProcessed":true,"requireReport":true,"requiredSubmitRole":%s}
                """
                .formatted(role);
    }

    private static RequestPostProcessor user(String role) {
        return jwt().jwt(token -> token.subject("user-" + role).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
