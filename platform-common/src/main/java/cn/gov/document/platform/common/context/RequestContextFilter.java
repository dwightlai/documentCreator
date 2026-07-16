package cn.gov.document.platform.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class RequestContextFilter extends OncePerRequestFilter {
    private final boolean localHeadersEnabled;

    public RequestContextFilter(@Value("${platform.security.local-header-enabled:false}") boolean localHeadersEnabled) {
        this.localHeadersEnabled = localHeadersEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
            .filter(StringUtils::hasText).orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        Identity identity = resolveIdentity(request);
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (localHeadersEnabled && !(previousAuthentication != null && previousAuthentication.getPrincipal() instanceof Jwt)) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                identity.userId(), null, java.util.List.of(
                    new SimpleGrantedAuthority("ROLE_platform.user"),
                    new SimpleGrantedAuthority("ROLE_platform.reviewer"),
                    new SimpleGrantedAuthority("ROLE_platform.admin"))));
        }
        response.setHeader("X-Request-Id", requestId);
        try {
            RequestContext.set(requestId, identity.tenantId(), identity.userId());
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    private Identity resolveIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (!StringUtils.hasText(tenantId)) {
                throw new IllegalStateException("认证令牌缺少 tenant_id");
            }
            return new Identity(tenantId, jwt.getSubject());
        }
        if (localHeadersEnabled) {
            return new Identity(
                headerOrDefault(request, "X-Tenant-Id", "demo-tenant"),
                headerOrDefault(request, "X-User-Id", "demo-user")
            );
        }
        return new Identity("unauthenticated", "anonymous");
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record Identity(String tenantId, String userId) {
    }
}
