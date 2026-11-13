package cn.gov.document.platform.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestContextFilter extends OncePerRequestFilter {
    private final boolean localHeadersEnabled;
    private final BiFunction<String, String, List<String>> localRoles;
    private final Function<String, Optional<SessionIdentity>> sessions;

    public RequestContextFilter(@Value("${platform.security.local-header-enabled:false}") boolean localHeadersEnabled) {
        this(localHeadersEnabled, (tenant, user) -> List.of("platform.user", "platform.reviewer", "platform.admin"));
    }

    public RequestContextFilter(boolean localHeadersEnabled, BiFunction<String, String, List<String>> localRoles) {
        this(localHeadersEnabled, localRoles, token -> Optional.empty());
    }

    public RequestContextFilter(
            boolean localHeadersEnabled,
            BiFunction<String, String, List<String>> localRoles,
            Function<String, Optional<SessionIdentity>> sessions) {
        this.localHeadersEnabled = localHeadersEnabled;
        this.localRoles = localRoles;
        this.sessions = sessions;
    }

    public record SessionIdentity(String tenantId, String username, List<String> roles) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
                .filter(StringUtils::hasText)
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        Identity identity = resolveIdentity(request);
        Authentication previousAuthentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(previousAuthentication != null && previousAuthentication.getPrincipal() instanceof Jwt)) {
            List<String> roles = identity.roles();
            if (roles == null && localHeadersEnabled) roles = localRoles.apply(identity.tenantId(), identity.userId());
            if (roles != null && (identity.session() || !roles.isEmpty())) {
                SecurityContextHolder.getContext()
                        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                                identity.userId(),
                                null,
                                roles.stream()
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                        .toList()));
            }
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

    public static final String OPEN_TENANT = "platform.open.tenant";
    public static final String OPEN_USER = "platform.open.user";

    private Identity resolveIdentity(HttpServletRequest request) {
        String openTenant = (String) request.getAttribute(OPEN_TENANT);
        if (StringUtils.hasText(openTenant)) {
            String openUser = (String) request.getAttribute(OPEN_USER);
            return new Identity(
                    openTenant, StringUtils.hasText(openUser) ? openUser : "open-app", List.of("open.app"), true);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (!StringUtils.hasText(tenantId)) {
                throw new IllegalStateException("认证令牌缺少 tenant_id");
            }
            return new Identity(tenantId, jwt.getSubject(), null, false);
        }
        String bearer = bearer(request);
        if (bearer != null) {
            var session = sessions.apply(bearer);
            if (session.isPresent()) {
                return new Identity(
                        session.get().tenantId(),
                        session.get().username(),
                        session.get().roles(),
                        true);
            }
            return new Identity("unauthenticated", "anonymous", List.of(), false);
        }
        if (localHeadersEnabled) {
            return new Identity(
                    headerOrDefault(request, "X-Tenant-Id", "demo-tenant"),
                    headerOrDefault(request, "X-User-Id", "demo-user"),
                    null,
                    false);
        }
        return new Identity("unauthenticated", "anonymous", null, false);
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record Identity(String tenantId, String userId, List<String> roles, boolean session) {}
}
