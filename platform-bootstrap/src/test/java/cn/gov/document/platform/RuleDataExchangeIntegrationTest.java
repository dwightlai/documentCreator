package cn.gov.document.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleDataExchangeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void exportsAndImportsTenantDataWithoutPublishingOrDuplicating() throws Exception {
        String source = "exchange-source";
        String target = "exchange-target";
        String rules = """
                {"format":"gov-document-rules","version":1,"rules":[
                  {"ruleCode":"EX-R-001","ruleName":"核对附件","category":"公文格式",
                   "applicableDocumentTypes":["通知"],"riskLevel":"一般",
                   "detectionMethod":"人工核验","ruleExpression":"核对附件名称和序号",
                   "basis":"GB/T 9704—2012 7.3.4"}]}""";
        mvc.perform(post("/api/v1/rules/import").header("X-Tenant-Id", source)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imported").value(1));
        JsonNode ruleBundle = mapper.readTree(mvc.perform(get("/api/v1/rules/export")
                        .header("X-Tenant-Id", source).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rules[0].basis").value("GB/T 9704—2012 7.3.4"))
                .andReturn().getResponse().getContentAsString()).path("data");
        mvc.perform(post("/api/v1/rules/import").header("X-Tenant-Id", target)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(ruleBundle)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imported").value(1));
        mvc.perform(post("/api/v1/rules/import").header("X-Tenant-Id", target)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(ruleBundle)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.skipped").value(1));
        mvc.perform(get("/api/v1/rules").header("X-Tenant-Id", target).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].lifecycleStatus").value("草稿"));

        String dictionaries = """
                {"format":"gov-document-dictionaries","version":1,"dictionaries":[
                  {"dictCode":"EX-D-001","dictName":"公文用语","dictType":"推荐词形",
                   "sourceDescription":"标准原文","terms":[
                     {"termText":"参预","standardForm":"参与","aliases":["參預"],
                      "categoryTag":"词形","riskLevel":"提示","sourceReference":"GF 1001—2001",
                      "usageNote":"结合语境核对"},
                     {"termText":"案语","standardForm":"按语","aliases":[]}
                   ]}]}""";
        mvc.perform(post("/api/v1/dictionaries/import").header("X-Tenant-Id", source)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON)
                        .content(dictionaries))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.termsImported").value(2));
        JsonNode dictionaryBundle = mapper.readTree(mvc.perform(get("/api/v1/dictionaries/export")
                        .header("X-Tenant-Id", source).header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictionaries[0].terms[0].sourceReference").value("GF 1001—2001"))
                .andReturn().getResponse().getContentAsString()).path("data");
        mvc.perform(post("/api/v1/dictionaries/import").header("X-Tenant-Id", target)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dictionaryBundle)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.termsImported").value(2));
        mvc.perform(get("/api/v1/dictionaries").header("X-Tenant-Id", target)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].entryCount").value(2))
                .andExpect(jsonPath("$.data[0].currentVersion").value(1));
        mvc.perform(post("/api/v1/dictionaries/import").header("X-Tenant-Id", target)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dictionaryBundle)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    void rejectsInvalidRuleBatchWithoutPartialImport() throws Exception {
        String tenant = "exchange-invalid";
        String invalid = """
                {"format":"gov-document-rules","version":1,"rules":[
                  {"ruleCode":"EX-GOOD","ruleName":"有效规则","category":"规范",
                   "riskLevel":"一般","detectionMethod":"词项","ruleExpression":"旧词=>新词"},
                  {"ruleCode":"EX-BAD","ruleName":"无效正则","category":"规范",
                   "riskLevel":"一般","detectionMethod":"正则","ruleExpression":"[=>新词"}]}""";
        mvc.perform(post("/api/v1/rules/import").header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/rules").header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
}
