package cn.gov.document.platform.config;

import cn.gov.document.platform.common.context.RequestContextFilter;
import cn.gov.document.platform.job.web.IdempotencyFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    RequestContextFilter platformRequestContextFilter(
            @Value("${platform.security.local-header-enabled:false}") boolean localHeadersEnabled,
            @Value("${platform.security.local-directory-roles:false}") boolean localDirectoryRoles,
            JdbcTemplate jdbc) {
        if (!localDirectoryRoles) return new RequestContextFilter(localHeadersEnabled);
        return new RequestContextFilter(localHeadersEnabled, (tenant, user) -> jdbc.queryForList(
                "SELECT r.role_code FROM platform_user u JOIN platform_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id JOIN platform_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id WHERE u.tenant_id=? AND u.username=? AND u.enabled=TRUE AND r.enabled=TRUE",
                String.class, tenant, user));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestContextFilter contextFilter,
            IdempotencyFilter idempotencyFilter,
            JwtAuthenticationConverter jwtConverter,
            @Value("${platform.security.local-header-enabled:false}") boolean localMode,
            @Value("${platform.integration.oa.allowed-client-ids:}") List<String> oaClientIds)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (localMode) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/api/v1/onlyoffice/files/*", "/api/v1/onlyoffice/callback/*").permitAll()
                            .anyRequest().authenticated())
                    .addFilterAfter(contextFilter, AnonymousAuthenticationFilter.class)
                    .addFilterAfter(idempotencyFilter, RequestContextFilter.class);
        } else {
            http.authorizeHttpRequests(
                            auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                                    .permitAll()
                                    // ONLYOFFICE 文档服务器拉取文件与保存回调：不经登录认证，由平台签发的访问令牌校验身份
                                    .requestMatchers("/api/v1/onlyoffice/files/*", "/api/v1/onlyoffice/callback/*")
                                    .permitAll()
                                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**")
                                    .hasRole("platform.admin")
                                    .requestMatchers("/api/v1/integrations/oa/**")
                                    .access((authentication, context) -> new AuthorizationDecision(
                                            oaClientAllowed(authentication.get(), oaClientIds)))
                                    .anyRequest()
                                    .access((authentication, context) -> new AuthorizationDecision(
                                            normalApiAllowed(authentication.get()))))
                    .oauth2ResourceServer(resource -> resource.bearerTokenResolver(platformBearerTokens())
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                    .addFilterAfter(contextFilter, BearerTokenAuthenticationFilter.class)
                    .addFilterAfter(idempotencyFilter, RequestContextFilter.class);
        }
        return http.build();
    }

    /** ONLYOFFICE 文档服务器的请求自带其签名的 Bearer 令牌，不是平台登录令牌，交由接口自行校验，不按 OIDC 令牌解析。 */
    private static BearerTokenResolver platformBearerTokens() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/v1/onlyoffice/files/")
                        || request.getRequestURI().startsWith("/api/v1/onlyoffice/callback/")
                ? null
                : resolver.resolve(request);
    }

    static boolean oaClientAllowed(Authentication authentication, List<String> allowedClientIds) {
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) return false;
        String clientId = token.getToken().getClaimAsString("azp");
        if (clientId == null) clientId = token.getToken().getClaimAsString("client_id");
        String resolvedClientId = clientId;
        return org.springframework.util.StringUtils.hasText(token.getToken().getClaimAsString("tenant_id"))
                && allowedClientIds.stream().anyMatch(id -> !id.isBlank() && id.equals(resolvedClientId))
                && token.getAuthorities().stream().anyMatch(authority ->
                        "SCOPE_oa.integration".equals(authority.getAuthority()));
    }

    static boolean normalApiAllowed(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().noneMatch(authority ->
                        "SCOPE_oa.integration".equals(authority.getAuthority()));
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(JdbcTemplate jdbc) {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map<?, ?> values && values.get("roles") instanceof Collection<?> roles) {
                roles.stream()
                        .map(String::valueOf)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(authorities::add);
            }
            String tenant = jwt.getClaimAsString("tenant_id");
            if (tenant != null && jwt.getSubject() != null) {
                jdbc.queryForList(
                        "SELECT r.role_code FROM platform_user u JOIN platform_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id JOIN platform_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id WHERE u.tenant_id=? AND u.username=? AND u.enabled=TRUE AND r.enabled=TRUE",
                        String.class, tenant, jwt.getSubject()).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${platform.security.allowed-origins:http://localhost:5175}") List<String> allowedOrigins,
            @Value("${platform.security.local-header-enabled:false}") boolean localMode) {
        CorsConfiguration configuration = new CorsConfiguration();
        // 本地开发可从不同局域网地址打开 Vite；正式环境仍使用明确的来源白名单。
        if (localMode) configuration.setAllowedOriginPatterns(List.of("*"));
        else configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Idempotency-Key",
                "If-Match",
                "X-Tenant-Id",
                "X-User-Id",
                "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Location", "ETag", "Idempotency-Replayed"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
