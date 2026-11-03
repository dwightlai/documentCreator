package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.model.domain.ModelConfigEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.*;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Component
public class OpenAiCompatibleModelClient {
    private final WebClient.Builder builder;
    private final ModelEndpointSecurity security;
    private final ModelGatewayProperties properties;

    public OpenAiCompatibleModelClient(
            WebClient.Builder builder, ModelEndpointSecurity security, ModelGatewayProperties properties) {
        this.builder = builder;
        this.security = security;
        this.properties = properties;
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
        // DeepSeek Flash defaults to high-effort thinking. Short document sections
        // then spend most of their latency and tokens on invisible reasoning.
        // Keep an explicit template setting authoritative for workflows that need it.
        if ("api.deepseek.com".equalsIgnoreCase(endpoint.getHost())
                && "deepseek-flash".equalsIgnoreCase(config.getModelName())) {
            Object thinking = parameters.get("thinking");
            if (thinking instanceof Map<?, ?> setting
                    && ("enabled".equals(setting.get("type")) || "disabled".equals(setting.get("type")))) {
                body.put("thinking", thinking);
            } else {
                body.put("thinking", Map.of("type", "disabled"));
            }
        }
        try {
            WebClient.RequestBodySpec request =
                    webClient(endpoint).post().uri(endpoint).contentType(MediaType.APPLICATION_JSON);
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
        } catch (WebClientRequestException e) {
            throw BusinessException.unprocessable("模型服务连接失败，请检查模型端点、网络及代理配置");
        } catch (RuntimeException e) {
            throw BusinessException.unprocessable("模型服务调用失败: " + e.getClass().getSimpleName());
        }
    }

    private WebClient webClient(URI endpoint) {
        String proxyUrl = properties.getProxyUrl();
        if (!StringUtils.hasText(proxyUrl) || bypassProxy(endpoint.getHost())) return builder.build();
        URI proxy;
        try {
            proxy = URI.create(proxyUrl.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.unprocessable("模型代理地址格式不正确");
        }
        if (!"http".equalsIgnoreCase(proxy.getScheme())
                || proxy.getHost() == null
                || proxy.getPort() < 1
                || proxy.getUserInfo() != null) {
            throw BusinessException.unprocessable("模型代理须配置为不含凭据的 HTTP 地址及端口");
        }
        HttpClient client = HttpClient.create()
                .proxy(spec -> spec.type(ProxyProvider.Proxy.HTTP).host(proxy.getHost()).port(proxy.getPort()));
        return builder.clone().clientConnector(new ReactorClientHttpConnector(client)).build();
    }

    private boolean bypassProxy(String host) {
        if (host == null || host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"))
            return true;
        String exclusions = System.getenv("NO_PROXY");
        if (!StringUtils.hasText(exclusions)) return false;
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String entry : exclusions.split(",")) {
            String excluded = entry.trim().toLowerCase(Locale.ROOT);
            if (excluded.equals("*")) return true;
            if (excluded.startsWith("*.")) excluded = excluded.substring(2);
            else if (excluded.startsWith(".")) excluded = excluded.substring(1);
            if (normalizedHost.equals(excluded) || normalizedHost.endsWith("." + excluded)) return true;
        }
        return false;
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
