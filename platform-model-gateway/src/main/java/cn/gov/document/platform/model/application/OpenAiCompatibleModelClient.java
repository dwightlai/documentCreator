package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.model.domain.ModelConfigEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.*;

@Component
public class OpenAiCompatibleModelClient {
    private final WebClient.Builder builder;
    private final ModelEndpointSecurity security;

    public OpenAiCompatibleModelClient(WebClient.Builder builder, ModelEndpointSecurity security) {
        this.builder = builder;
        this.security = security;
    }

    public Result invoke(
            ModelConfigEntity config, String systemPrompt, String userInput, Map<String, Object> parameters) {
        URI endpoint = security.validate(config.getEndpointUrl());
        String secret = resolveCredential(config.getCredentialRef());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", PromptInjectionDefense.secureSystemPrompt(systemPrompt)),
                        Map.of("role", "user", "content", PromptInjectionDefense.secureUserInput(userInput))));
        copyNumber(parameters, body, "temperature");
        copyNumber(parameters, body, "top_p");
        copyNumber(parameters, body, "max_tokens");
        try {
            WebClient.RequestBodySpec request =
                    builder.build().post().uri(endpoint).contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(secret)) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + secret);
            JsonNode json = request.bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofMillis(config.getTimeoutMs()));
            if (json == null) throw BusinessException.unprocessable("模型返回空响应");
            String content =
                    json.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content))
                throw BusinessException.unprocessable("模型响应缺少 choices[0].message.content");
            int input = json.path("usage").path("prompt_tokens").asInt(0);
            int output = json.path("usage").path("completion_tokens").asInt(0);
            return new Result(content, input, output);
        } catch (WebClientResponseException e) {
            throw BusinessException.unprocessable(
                    "模型服务返回 HTTP " + e.getStatusCode().value());
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw BusinessException.unprocessable("模型服务调用失败: " + e.getClass().getSimpleName());
        }
    }

    private String resolveCredential(String ref) {
        if (!StringUtils.hasText(ref)) return null;
        if (!ref.matches("[A-Z][A-Z0-9_]{2,99}")) throw BusinessException.unprocessable("模型密钥引用名称不合法");
        String value = System.getenv(ref);
        if (!StringUtils.hasText(value)) throw BusinessException.unprocessable("模型密钥环境变量未配置: " + ref);
        return value;
    }

    private void copyNumber(Map<String, Object> from, Map<String, Object> to, String key) {
        Object value = from.get(key);
        if (value instanceof Number) to.put(key, value);
    }

    public record Result(String output, int inputTokens, int outputTokens) {}
}
