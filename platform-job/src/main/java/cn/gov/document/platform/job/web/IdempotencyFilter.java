package cn.gov.document.platform.job.web;

import cn.gov.document.platform.job.application.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

public class IdempotencyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final int MAX_REQUEST_BYTES = 25 * 1024 * 1024;

    private final IdempotencyService service;

    public IdempotencyFilter(IdempotencyService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (!MUTATING_METHODS.contains(request.getMethod()) || !StringUtils.hasText(key)) {
            chain.doFilter(request, response);
            return;
        }
        key = key.trim();
        if (!VALID_KEY.matcher(key).matches()) {
            writeError(response, 400, "Idempotency-Key 长度须为 8—128，且只能包含字母、数字、点、下划线、冒号和连字符");
            return;
        }
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            writeError(response, 413, "带幂等键的请求体不能超过 25MB");
            return;
        }

        byte[] requestBody = request.getInputStream().readAllBytes();
        if (requestBody.length > MAX_REQUEST_BYTES) {
            writeError(response, 413, "带幂等键的请求体不能超过 25MB");
            return;
        }
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        String requestHash = sha256(request.getMethod() + "\n" + request.getRequestURI() + "\n"
                + String.valueOf(request.getQueryString()) + "\n", requestBody);
        IdempotencyService.Reservation reservation = service.reserve(endpoint, key, requestHash);

        switch (reservation.decision()) {
            case REPLAY -> replay(response, reservation);
            case HASH_CONFLICT -> writeError(response, 409, "同一 Idempotency-Key 不能用于不同请求");
            case IN_PROGRESS -> writeError(response, 409, "同一幂等请求正在处理中，请稍后重试");
            case PROCEED -> executeAndCache(
                    new CachedBodyHttpServletRequest(request, requestBody), response, chain, reservation.recordId());
        }
    }

    private void executeAndCache(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            String recordId) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean chainReturned = false;
        try {
            chain.doFilter(request, wrapper);
            chainReturned = true;
            service.complete(recordId, wrapper.getStatus(), wrapper.getContentType(), wrapper.getContentAsByteArray());
        } finally {
            if (!chainReturned) {
                try {
                    service.abandon(recordId);
                } catch (RuntimeException cleanupFailure) {
                    log.error("Failed to abandon idempotency reservation {}", recordId, cleanupFailure);
                }
            }
            wrapper.copyBodyToResponse();
        }
    }

    private void replay(HttpServletResponse response, IdempotencyService.Reservation reservation) throws IOException {
        response.setStatus(reservation.httpStatus() == null ? 200 : reservation.httpStatus());
        if (StringUtils.hasText(reservation.contentType())) {
            response.setContentType(reservation.contentType());
        }
        response.setHeader("Idempotency-Replayed", "true");
        response.getOutputStream().write(reservation.responseBody());
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"code\":" + status + ",\"message\":\"" + message + "\"}";
        response.getWriter().write(body);
    }

    private String sha256(String prefix, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
