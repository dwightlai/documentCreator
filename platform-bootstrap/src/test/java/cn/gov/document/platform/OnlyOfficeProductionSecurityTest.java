package cn.gov.document.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 生产安全链（关闭本地身份请求头）：ONLYOFFICE 拉取文件与保存回调两个入口不经 OIDC 登录，文档服务器自带的 Bearer 令牌
 * 不按 OIDC 令牌解析（否则会被直接拒绝），由接口自行校验；其他接口仍要求登录。
 */
@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "platform.security.local-header-enabled=false",
            "platform.onlyoffice.enabled=true",
            "platform.onlyoffice.public-url=https://platform.example.gov.cn/office",
            "platform.onlyoffice.callback-base-url=http://backend:18089",
            "platform.onlyoffice.jwt-secret=onlyoffice-test-secret-0123456789abcdef"
        })
@AutoConfigureMockMvc
class OnlyOfficeProductionSecurityTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void documentServerEndpointsBypassOidcButEverythingElseRequiresLogin() throws Exception {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("not an OIDC token"));

        mockMvc.perform(get("/api/v1/writing-tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/onlyoffice/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/writing-tasks").header(HttpHeaders.AUTHORIZATION, "Bearer forged"))
                .andExpect(status().isUnauthorized());

        // 到达接口后由平台访问令牌校验：无效令牌返回业务错误，而不是被 OIDC 认证拦截
        mockMvc.perform(post("/api/v1/onlyoffice/callback/not-a-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer document-server-signed-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"x\",\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(1));
        mockMvc.perform(get("/api/v1/onlyoffice/files/not-a-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer document-server-signed-token"))
                .andExpect(status().isUnprocessableEntity());
    }
}
