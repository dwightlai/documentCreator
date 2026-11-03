package cn.gov.document.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ConfigurationCrudIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void modelRouteCanBeViewedEditedAndDeletedWhenInactive() throws Exception {
        String tenant = "tenant-model-crud";
        String body =
                """
                {"configCode":"CRUD-MODEL","configName":"初始路由","provider":"本地私有","modelName":"local-model",
                 "scene":"段落生成","dataLevel":"内部","maxContextTokens":8192,"timeoutMs":30000,
                 "maxRetries":1,"sensitiveWordFilter":true,"outputValidation":true,"active":false}
                """;
        JsonNode created = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/model-configs")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data");
        String id = created.path("id").asText();
        mockMvc.perform(get("/api/v1/model-configs/{id}", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configName").value("初始路由"));
        mockMvc.perform(put("/api/v1/model-configs/{id}", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("初始路由", "修改后路由")
                                .replace(
                                        "\"active\":false",
                                        "\"active\":false,\"lockVersion\":"
                                                + created.path("lockVersion").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configName").value("修改后路由"));
        mockMvc.perform(delete("/api/v1/model-configs/{id}", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/model-configs/{id}", id)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void promptAndDocumentTypeCanBeViewedEditedAndDeleted() throws Exception {
        String tenant = "tenant-governance-crud";
        String promptBody =
                """
                {"templateCode":"CRUD-PROMPT","templateName":"初始模板","applicableScene":"段落生成",
                 "systemRole":"系统角色","antiFabricationRule":"禁止编造","outputFormat":"纯文本","changeDescription":"创建"}
                """;
        JsonNode prompt = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/prompt-templates")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(promptBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data");
        String promptId = prompt.path("id").asText();
        mockMvc.perform(get("/api/v1/prompt-templates/{id}", promptId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateName").value("初始模板"));
        mockMvc.perform(put("/api/v1/prompt-templates/{id}", promptId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promptBody
                                .replace("初始模板", "修改后模板")
                                .replace(
                                        "\"创建\"",
                                        "\"修改\",\"lockVersion\":"
                                                + prompt.path("lockVersion").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2));
        mockMvc.perform(delete("/api/v1/prompt-templates/{id}", promptId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/prompt-templates/{id}/versions", promptId)
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isNotFound());

        String typeBody =
                """
                {"typeCode":"CRUD-TYPE","typeName":"初始文种","documentCategory":"事务文书","direction":"内部",
                 "requiredFields":["标题"],"recommendedStructure":["正文"],"changeDescription":"创建"}
                """;
        JsonNode type = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/document-type-configs")
                                .header("X-Tenant-Id", tenant)
                                .header("X-User-Id", "admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(typeBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data");
        mockMvc.perform(get("/api/v1/document-type-configs/CRUD-TYPE").header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeName").value("初始文种"));
        mockMvc.perform(put("/api/v1/document-type-configs/CRUD-TYPE")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typeBody.replace("\"typeCode\":\"CRUD-TYPE\",", "")
                                .replace("初始文种", "修改后文种")
                                .replace(
                                        "\"创建\"",
                                        "\"修改\",\"lockVersion\":"
                                                + type.path("lockVersion").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2));
        mockMvc.perform(delete("/api/v1/document-type-configs/CRUD-TYPE")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", "admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/document-type-configs/CRUD-TYPE").header("X-Tenant-Id", tenant))
                .andExpect(status().isNotFound());
    }
}
