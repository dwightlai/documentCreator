package cn.gov.document.platform.openapp;

import cn.gov.document.platform.common.context.RequestContextFilter;
import cn.gov.document.platform.config.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class OpenAppAuthenticationFilter extends OncePerRequestFilter {
    static final String APP_CODE = "X-Open-App-Code";
    static final String API_KEY = "X-Open-Api-Key";
    private final OpenAppService apps;
    private final List<String> oauthClientIds;

    public OpenAppAuthenticationFilter(OpenAppService apps, List<String> oauthClientIds) {
        this.apps = apps;
        this.oauthClientIds = oauthClientIds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        if (!path.startsWith("/api/v1/integrations/oa/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String code = request.getHeader(APP_CODE);
        String key = request.getHeader(API_KEY);
        if (!StringUtils.hasText(code) && !StringUtils.hasText(key)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (SecurityConfiguration.oaClientAllowed(authentication, oauthClientIds)) {
                chain.doFilter(request, response);
                return;
            }
            write(response, 401, "请在请求头提供 X-Open-App-Code 与 X-Open-Api-Key");
            return;
        }
        OpenAppService.AuthDecision decision = apps.authenticate(
                code,
                key,
                OpenAppService.capability(path),
                request.getHeader("X-Open-User-Id"),
                request.getHeader("X-Open-Unit-Id"),
                request.getHeader("X-Open-Department-Id"),
                request.getHeader("X-Open-Role-Ids"),
                path,
                request.getRemoteAddr());
        if (!decision.ok()) {
            write(response, decision.status(), decision.message());
            return;
        }
        request.setAttribute(RequestContextFilter.OPEN_TENANT, decision.tenantId());
        request.setAttribute(RequestContextFilter.OPEN_USER, decision.appId());
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
