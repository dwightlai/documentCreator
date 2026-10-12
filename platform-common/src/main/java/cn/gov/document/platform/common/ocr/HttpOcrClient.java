package cn.gov.document.platform.common.ocr;

import cn.gov.document.platform.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通过 HTTP 调用单位内网部署的 OCR 服务（如 PaddleOCR 的服务化封装）。
 *
 * <p>约定：POST JSON {@code {"filename","contentType","content":"<base64>"}}，返回 {@code {"text":"..."}}（也兼容
 * {@code {"data":{"text":"..."}}}）。服务地址由管理员通过 {@code platform.ocr.endpoint} 配置，访问令牌只通过环境变量名引用。
 * 文件内容会发送到该服务，必须部署在与本平台同等安全级别的内网环境中。
 */
@Component
public class HttpOcrClient implements OcrPort {
    private static final Logger log = LoggerFactory.getLogger(HttpOcrClient.class);

    private final String endpoint;
    private final Duration timeout;
    private final String tokenEnvironmentVariable;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public HttpOcrClient(
            @Value("${platform.ocr.endpoint:}") String endpoint,
            @Value("${platform.ocr.timeout:60s}") Duration timeout,
            @Value("${platform.ocr.token-env:}") String tokenEnvironmentVariable,
            ObjectMapper objectMapper) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.timeout = timeout;
        this.tokenEnvironmentVariable = tokenEnvironmentVariable;
        this.objectMapper = objectMapper;
        if (StringUtils.hasText(this.endpoint)) {
            String scheme = URI.create(this.endpoint).getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                throw new IllegalStateException("platform.ocr.endpoint 只支持 http 或 https");
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean enabled() {
        return StringUtils.hasText(endpoint);
    }

    @Override
    public Optional<String> recognize(String filename, String contentType, byte[] content) {
        if (!enabled()) return Optional.empty();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filename", filename);
            body.put("contentType", contentType);
            body.put("content", Base64.getEncoder().encodeToString(content));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            String token =
                    StringUtils.hasText(tokenEnvironmentVariable) ? System.getenv(tokenEnvironmentVariable) : null;
            if (StringUtils.hasText(token)) request.header("Authorization", "Bearer " + token);
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("OCR 服务返回状态 {}", response.statusCode());
                throw BusinessException.unprocessable("OCR 服务识别失败，请稍后重试或人工录入材料");
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode text =
                    json.has("text") ? json.get("text") : json.path("data").path("text");
            return text.isTextual() && StringUtils.hasText(text.asText())
                    ? Optional.of(text.asText())
                    : Optional.empty();
        } catch (IOException e) {
            log.warn("OCR 服务调用失败：{}", e.getMessage());
            throw BusinessException.unprocessable("OCR 服务调用失败，请稍后重试或人工录入材料");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.unprocessable("OCR 识别被中断");
        }
    }
}
