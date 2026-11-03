package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.gov.document.platform.common.model.ModelGenerationPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentRegenerationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean cn.gov.document.platform.model.application.ModelGatewayService model;

    JsonNode call(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("X-Tenant-Id", "regeneration-test").header("X-User-Id", "editor");
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    void editsFeedRegenerationAndReplacementPreservesHistoryWithoutExtraParagraphs() throws Exception {
        var inputs = new ArrayList<String>();
        when(model.generate(eq("段落生成"), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    inputs.add(invocation.getArgument(3));
                    return Optional.of(new ModelGenerationPort.Result("重新生成正文第" + inputs.size() + "次", "model", "test", "prompt", 1, 10, 10, 1));
                });
        JsonNode task = call(post("/api/v1/writing-tasks"), """
                {"taskName":"重新生成回归","documentTypeCode":"请示","documentCategory":"法定公文",
                 "writingTopic":"设备更新","writingPurpose":"申请专项资金","expectedLengthMin":1200,"expectedLengthMax":2400,"securityLevel":"内部"}
                """);
        String base = "/api/v1/writing-tasks/" + task.path("id").asText();
        JsonNode outline = call(post(base + "/outlines"), null);
        call(post(base + "/outlines/" + outline.path("version").asInt() + "/confirm"), null);
        int count = outline.path("nodes").size();
        String nodeId = outline.path("nodes").get(0).path("id").asText();
        JsonNode original = call(post(base + "/document/rebuild"), "{\"confirmed\":true}");
        assertThat(original.path("paragraphs").size()).isEqualTo(count);
        JsonNode first = original.path("paragraphs").get(0);
        JsonNode edited = call(put(base + "/document/paragraphs/" + first.path("id").asText()),
                "{\"plainText\":\"人工明确预算为五十万元\",\"lockVersion\":" + first.path("lockVersion").asInt() + "}");
        JsonNode section = call(post(base + "/generate/section"), "{\"outlineNodeId\":\"" + nodeId + "\",\"instruction\":\"突出必要性\"}");
        assertThat(section.path("paragraphs").size()).isEqualTo(count);
        assertThat(inputs.get(inputs.size() - 1)).contains("人工明确预算为五十万元", "申请专项资金", "突出必要性");
        JsonNode current = section.path("paragraphs").get(0);
        call(put(base + "/document/paragraphs/" + current.path("id").asText()),
                "{\"plainText\":\"人工补充第二次修改\",\"lockVersion\":" + current.path("lockVersion").asInt() + "}");
        int before = inputs.size();
        JsonNode rebuilt = call(post(base + "/document/rebuild"), "{\"confirmed\":true}");
        assertThat(inputs.get(before)).contains("人工补充第二次修改");
        assertThat(rebuilt.path("paragraphs").size()).isEqualTo(count);
        int version = rebuilt.path("currentVersion").asInt();
        JsonNode removed = call(delete(base + "/document/paragraphs/" + rebuilt.path("paragraphs").get(0).path("id").asText()), null);
        assertThat(removed.path("paragraphs").size()).isEqualTo(count - 1);
        assertThat(call(get(base + "/document/versions/" + version), null).path("paragraphs").size()).isEqualTo(count);
        assertThat(call(post(base + "/document/versions/" + version + "/restore"), null).path("paragraphs").size()).isEqualTo(count);
    }
}
