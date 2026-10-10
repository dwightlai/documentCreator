package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AsyncJobApiIntegrationTest {
    private static final String TENANT = "tenant-job";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void persistsJobProgressReplaysSseAndIsolatesTenants() throws Exception {
        String requestBody = "{\"jobType\":\"SYSTEM_HEALTH_CHECK\",\"businessId\":\"acceptance-1\"}";
        MvcResult submitted = mockMvc.perform(post("/api/v1/jobs")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "job-admin")
                        .header("Idempotency-Key", "job-health-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andReturn();
        String jobId = objectMapper
                .readTree(submitted.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();
        assertThat(jobId).hasSize(32);

        mockMvc.perform(post("/api/v1/jobs")
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "job-admin")
                        .header("Idempotency-Key", "job-health-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        JsonNode job = waitForTerminal(jobId);
        assertThat(job.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.path("progress").asInt()).isEqualTo(100);

        String historyBody = mockMvc.perform(get("/api/v1/jobs/{jobId}/events/history", jobId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "job-admin"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode events = objectMapper.readTree(historyBody).path("data");
        assertThat(events).hasSizeGreaterThanOrEqualTo(4);
        assertThat(events.get(0).path("event").asText()).isEqualTo("start");
        assertThat(events.get(events.size() - 1).path("event").asText()).isEqualTo("completed");
        for (int index = 0; index < events.size(); index++) {
            assertThat(events.get(index).path("id").asInt()).isEqualTo(index + 1);
        }

        MvcResult stream = mockMvc.perform(get("/api/v1/jobs/{jobId}/events", jobId)
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "job-admin")
                        .header("Last-Event-ID", "1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String sseBody = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(sseBody).contains("event:completed").doesNotContain("id:1\n");

        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId)
                        .header("X-Tenant-Id", "other-job-tenant")
                        .header("X-User-Id", "other-admin"))
                .andExpect(status().isNotFound());
    }

    private JsonNode waitForTerminal(String jobId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            String body = mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId)
                            .header("X-Tenant-Id", TENANT)
                            .header("X-User-Id", "job-admin"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode job = objectMapper.readTree(body).path("data");
            if (java.util.Set.of("COMPLETED", "FAILED", "DEAD_LETTER", "CANCELLED")
                    .contains(job.path("status").asText())) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("异步任务未在 3 秒内完成");
    }
}
