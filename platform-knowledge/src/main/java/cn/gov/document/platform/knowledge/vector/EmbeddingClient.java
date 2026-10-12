package cn.gov.document.platform.knowledge.vector;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI-compatible 向量化接口客户端（§26.2A）。请求 {@code {"model","input":[...]}}，响应 {@code {"data":[{"index","embedding"}]}}。
 *
 * <p>文本会发送到该服务，必须部署在与本平台同等安全级别的内网。未配置地址时不启用向量检索，知识检索保持全文模式。
 */
@Component
public class EmbeddingClient {
    private final String endpoint;
    private final String model;
    private final String tokenEnvironmentVariable;
    private final int batchSize;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public EmbeddingClient(
            @Value("${platform.knowledge.embedding.endpoint:}") String endpoint,
            @Value("${platform.knowledge.embedding.model:bge-m3}") String model,
            @Value("${platform.knowledge.embedding.token-env:}") String tokenEnvironmentVariable,
            @Value("${platform.knowledge.embedding.batch-size:16}") int batchSize,
            @Value("${platform.knowledge.embedding.timeout:30s}") Duration timeout,
            ObjectMapper objectMapper) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.model = model;
        this.tokenEnvironmentVariable = tokenEnvironmentVariable;
        this.batchSize = Math.max(1, batchSize);
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        if (StringUtils.hasText(this.endpoint)) {
            String scheme = URI.create(this.endpoint).getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                throw new IllegalStateException("platform.knowledge.embedding.endpoint 只支持 http 或 https");
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean enabled() {
        return StringUtils.hasText(endpoint);
    }

    public String model() {
        return model;
    }

    public List<float[]> embed(List<String> texts) {
        if (!enabled()) throw BusinessException.unprocessable("向量化服务尚未配置");
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            vectors.addAll(embedBatch(texts.subList(from, Math.min(texts.size(), from + batchSize))));
        }
        return vectors;
    }

    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    private List<float[]> embedBatch(List<String> batch) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("model", model, "input", batch))));
            String token =
                    StringUtils.hasText(tokenEnvironmentVariable) ? System.getenv(tokenEnvironmentVariable) : null;
            if (StringUtils.hasText(token)) request.header("Authorization", "Bearer " + token);
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2)
                throw BusinessException.unprocessable("向量化服务返回错误状态 " + response.statusCode());
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() != batch.size())
                throw BusinessException.unprocessable("向量化服务返回的结果数量与输入不一致");
            float[][] ordered = new float[batch.size()][];
            for (int i = 0; i < data.size(); i++) {
                JsonNode item = data.get(i);
                int index = item.has("index") ? item.get("index").asInt() : i;
                JsonNode embedding = item.path("embedding");
                float[] vector = new float[embedding.size()];
                for (int d = 0; d < vector.length; d++)
                    vector[d] = (float) embedding.get(d).asDouble();
                if (index < 0 || index >= ordered.length || vector.length == 0)
                    throw BusinessException.unprocessable("向量化服务返回的结果格式不正确");
                ordered[index] = vector;
            }
            return List.of(ordered);
        } catch (IOException e) {
            throw BusinessException.unprocessable("向量化服务调用失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.unprocessable("向量化被中断");
        }
    }
}
