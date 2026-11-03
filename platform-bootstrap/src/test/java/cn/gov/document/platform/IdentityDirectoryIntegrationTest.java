package cn.gov.document.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

@SpringBootTest(properties = "platform.security.local-directory-roles=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityDirectoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void organizationRoleAndUserArePersistedAndTenantScoped() throws Exception {
        String orgId = create("/api/v1/identity/organizations", "{\"orgCode\":\"office\",\"orgName\":\"办公室\",\"enabled\":true}");
        String roleId = create("/api/v1/identity/roles", "{\"roleCode\":\"platform.reviewer\",\"roleName\":\"核稿人员\",\"enabled\":true}");
        String userId = create("/api/v1/identity/users", "{\"username\":\"reviewer\",\"displayName\":\"张三\",\"orgId\":\"" + orgId + "\",\"roleIds\":[\"" + roleId + "\"],\"enabled\":true}");
        mvc.perform(get("/api/v1/identity/users").with(admin("other-tenant"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/identity/users").with(admin("identity-test"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roleIds[0]").value(roleId));
        mvc.perform(delete("/api/v1/identity/roles/{id}", roleId).with(admin("identity-test"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/identity/users/{id}", userId).with(admin("identity-test"))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/identity/roles/{id}", roleId).with(admin("identity-test"))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/identity/organizations/{id}", orgId).with(admin("identity-test"))).andExpect(status().isOk());
    }

    @Test
    void ordinaryUserCannotManageDirectory() throws Exception {
        mvc.perform(post("/api/v1/identity/roles").with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "identity-test"))
                        .authorities(new SimpleGrantedAuthority("ROLE_platform.user")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"x\",\"roleName\":\"X\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void localHeadersUseStoredAssignmentsAndRejectUnknownUsers() throws Exception {
        mvc.perform(get("/api/v1/identity/me")
                        .header("X-Tenant-Id", "local-government").header("X-User-Id", "local-user"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles[0]").value("platform.admin"));
        mvc.perform(get("/api/v1/identity/users")
                        .header("X-Tenant-Id", "local-government").header("X-User-Id", "missing-user"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/writing-tasks")
                        .header("X-Tenant-Id", "local-government").header("X-User-Id", "missing-user"))
                .andExpect(status().isForbidden());
    }

    private String create(String path, String body) throws Exception {
        String response = mvc.perform(post(path).with(admin("identity-test"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asText();
    }

    private static RequestPostProcessor admin(String tenant) {
        return jwt().jwt(token -> token.subject("admin").claim("tenant_id", tenant))
                .authorities(new SimpleGrantedAuthority("ROLE_platform.admin"));
    }
}
