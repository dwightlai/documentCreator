package cn.gov.document.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OaClientAccessTest {
    @Test
    void onlyAllowlistedTenantBoundMachineTokensCanUseOaApi() {
        var allowed = List.of("oa-client");
        assertThat(SecurityConfiguration.oaClientAllowed(token("oa-client", "tenant-a", true), allowed))
                .isTrue();
        assertThat(SecurityConfiguration.oaClientAllowed(token("other", "tenant-a", true), allowed))
                .isFalse();
        assertThat(SecurityConfiguration.oaClientAllowed(token("oa-client", null, true), allowed))
                .isFalse();
        assertThat(SecurityConfiguration.oaClientAllowed(token("oa-client", "tenant-a", false), allowed))
                .isFalse();
        assertThat(SecurityConfiguration.oaClientAllowed(token("oa-client", "tenant-a", true), List.of()))
                .isFalse();
        assertThat(SecurityConfiguration.normalApiAllowed(token("oa-client", "tenant-a", true)))
                .isFalse();
        assertThat(SecurityConfiguration.normalApiAllowed(token("web-client", "tenant-a", false)))
                .isTrue();
    }

    private static JwtAuthenticationToken token(String clientId, String tenant, boolean oaScope) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("service-account")
                .claim("azp", clientId);
        if (tenant != null) builder.claim("tenant_id", tenant);
        return new JwtAuthenticationToken(
                builder.build(), oaScope ? List.of(new SimpleGrantedAuthority("SCOPE_oa.integration")) : List.of());
    }
}
