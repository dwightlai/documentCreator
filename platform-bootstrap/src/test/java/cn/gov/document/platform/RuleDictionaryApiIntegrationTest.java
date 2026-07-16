package cn.gov.document.platform;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.rule.application.EffectiveReviewRuleProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class RuleDictionaryApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EffectiveReviewRuleProvider effectiveReviewRuleProvider;

    @Test
    void enforcesRuleLifecycleAndImportsDictionaryTerms() throws Exception {
        String tenant="tenant-rules";
        String createdBody=mockMvc.perform(post("/api/v1/rules").header("X-Tenant-Id",tenant).header("X-User-Id","admin-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"ruleCode":"WORD-DEADLINE-001","ruleName":"截至用语规范","category":"规范",
                     "applicableDocumentTypes":["请示","报告"],"riskLevel":"一般","detectionMethod":"词项",
                     "ruleExpression":"截止目前为止=>截至目前","basis":"现代汉语规范用法","changeDescription":"建立初版"}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycleStatus").value("草稿"))
            .andReturn().getResponse().getContentAsString();
        String ruleId=objectMapper.readTree(createdBody).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/rules/{id}/submit-for-review",ruleId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/rules/{id}/test",ruleId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"cases":[{"text":"截止目前为止，工作进展顺利。","expectedMatchCount":1},
                               {"text":"截至目前，工作进展顺利。","expectedMatchCount":0}]}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.passed").value(true));
        mockMvc.perform(post("/api/v1/rules/{id}/submit-for-review",ruleId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycleStatus").value("审核"));
        mockMvc.perform(post("/api/v1/rules/{id}/publish",ruleId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycleStatus").value("生效"));
        mockMvc.perform(get("/api/v1/rules/{id}/versions",ruleId).header("X-Tenant-Id",tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].approvedBy").value("admin-1"));
        mockMvc.perform(get("/api/v1/rules").header("X-Tenant-Id","other-rule-tenant"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        String dictionaryBody=mockMvc.perform(post("/api/v1/dictionaries").header("X-Tenant-Id",tenant).header("X-User-Id","admin-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"dictCode":"COMMON-MISTAKES","dictName":"常见易错词","dictType":"易错词","sourceDescription":"人工核定"}
                    """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String dictionaryId=objectMapper.readTree(dictionaryBody).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/dictionaries/{id}/terms",dictionaryId).header("X-Tenant-Id",tenant).header("X-User-Id","admin-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"termText":"帐号","standardForm":"账号","aliases":["账户号"],"categoryTag":"信息化","riskLevel":"提示"}
                    """))
            .andExpect(status().isOk());

        String csv="term_text,standard_form,aliases,category_tag,risk_level,source_reference,effective_date,expiry_date,usage_note\n"
            +"做出贡献,作出贡献,,规范用语,一般,人工核定,,,固定搭配\n"
            +"截止目前,截至目前,截止到目前|截止当前,时间用语,一般,人工核定,2026-01-01,,用于时间截止表达\n"
            +"帐号,账号,,信息化,提示,重复样本,,,\n";
        MockMultipartFile file=new MockMultipartFile("file","terms.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/dictionaries/{id}/import",dictionaryId).file(file)
                .header("X-Tenant-Id",tenant).header("X-User-Id","admin-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedRows").value(2))
            .andExpect(jsonPath("$.data.skippedRows").value(1));
        mockMvc.perform(get("/api/v1/dictionaries/{id}",dictionaryId).header("X-Tenant-Id",tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.entryCount").value(3));
        mockMvc.perform(get("/api/v1/dictionaries/{id}/terms",dictionaryId).header("X-Tenant-Id",tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));

        RequestContext.set("rule-provider-test", tenant, "reviewer-1");
        try {
            var matches=effectiveReviewRuleProvider.inspect("请示", "截止目前为止，请登陆帐号核验。做出贡献。 ");
            assertThat(matches).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("截止目前为止");
                assertThat(match.replacement()).isEqualTo("截至目前");
            });
            assertThat(matches).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("帐号");
                assertThat(match.replacement()).isEqualTo("账号");
            });
            assertThat(matches).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("做出贡献");
                assertThat(match.replacement()).isEqualTo("作出贡献");
            });
        } finally {
            RequestContext.clear();
        }
    }
}
