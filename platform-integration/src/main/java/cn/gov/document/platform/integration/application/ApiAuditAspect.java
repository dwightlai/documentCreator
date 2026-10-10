package cn.gov.document.platform.integration.application;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(100)
public class ApiAuditAspect {
    private static final Logger log = LoggerFactory.getLogger(ApiAuditAspect.class);
    private static final Pattern ID = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])");
    private static final int MAX_ROLE_LENGTH = 500;

    private final AuditService service;

    public ApiAuditAspect(AuditService service) {
        this.service = service;
    }

    @Around("execution(* cn.gov.document.platform..api..*(..))")
    public Object audit(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return point.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String method = request.getMethod();
        if (!List.of("POST", "PUT", "PATCH", "DELETE").contains(method)
                || request.getRequestURI().startsWith("/api/v1/audit-logs")) {
            return point.proceed();
        }

        long started = System.nanoTime();
        boolean success = false;
        String error = null;
        try {
            Object result = point.proceed();
            success = true;
            return result;
        } catch (Throwable throwable) {
            error = throwable.getClass().getSimpleName();
            throw throwable;
        } finally {
            appendSafely(request, method, started, success, error);
        }
    }

    private void appendSafely(HttpServletRequest request, String method, long started, boolean success, String error) {
        try {
            long duration = (System.nanoTime() - started) / 1_000_000L;
            String uri = request.getRequestURI();
            Matcher matcher = ID.matcher(uri);
            String targetId = "collection";
            while (matcher.find()) {
                targetId = matcher.group();
            }

            String[] parts = uri.split("/");
            String targetType = parts.length > 3 ? parts[3] : "api";
            String roles = SecurityContextHolder.getContext().getAuthentication() == null
                    ? ""
                    : SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                            .map(Object::toString)
                            .sorted()
                            .collect(Collectors.joining(","));
            if (roles.length() > MAX_ROLE_LENGTH) {
                roles = roles.substring(0, MAX_ROLE_LENGTH);
            }

            String detail = "{\"success\":" + success
                    + ",\"durationMs\":" + duration
                    + ",\"errorType\":" + (error == null ? "null" : "\"" + error + "\"")
                    + ",\"requestBodyLogged\":false}";
            service.append(
                    auditType(uri),
                    roles,
                    targetType,
                    targetId,
                    method,
                    detail,
                    header(request, "X-Data-Level", "内部"),
                    clientIp(request));
        } catch (RuntimeException auditFailure) {
            // 审计存储故障不应掩盖原业务结果；运维监控必须对该日志告警。
            log.error("Failed to append API audit log for {} {}", method, request.getRequestURI(), auditFailure);
        }
    }

    private String auditType(String uri) {
        if (uri.contains("export") || uri.contains("reports")) {
            return "下载导出";
        }
        if (uri.contains("rules")
                || uri.contains("dictionaries")
                || uri.contains("output-templates")
                || uri.contains("document-type-configs")
                || uri.contains("prompt-templates")) {
            return "规则变更";
        }
        if (uri.contains("review")) {
            return "问题处理";
        }
        if (uri.contains("knowledge")) {
            return "知识使用";
        }
        if (uri.contains("model")) {
            return "模型调用";
        }
        return "访问";
    }

    private String clientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (value != null && !value.isBlank()) {
            return value.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
