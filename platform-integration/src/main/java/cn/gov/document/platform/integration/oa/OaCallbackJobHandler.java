package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.job.application.AsyncJobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

@Component
public class OaCallbackJobHandler implements AsyncJobHandler {
    private final ObjectMapper objectMapper;
    private final OaCallbackSecurity security;
    private final OaIntegrationProperties properties;
    private final WebClient webClient;

    public OaCallbackJobHandler(
            ObjectMapper objectMapper,
            OaCallbackSecurity security,
            OaIntegrationProperties properties,
            WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.security = security;
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String jobType() {
        return "OA_CALLBACK";
    }

    @Override
    public String execute(String payload, ProgressReporter reporter) throws Exception {
        JsonNode command = objectMapper.readTree(payload);
        String callbackUrl = command.path("callbackUrl").asText();
        String body = objectMapper.writeValueAsString(command.path("body"));
        var uri = security.validateCallbackUrl(callbackUrl);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String signature = security.sign(timestamp, nonce, body);
        reporter.progress(20, "正在回调 OA");
        webClient.post()
                .uri(uri)
                .header("X-Callback-Timestamp", timestamp)
                .header("X-Callback-Nonce", nonce)
                .header("X-Callback-Signature", signature)
                .header("Content-Type", "application/json")
                // 以 JSON 节点交给 Jackson 编码，避免把已序列化 JSON 再编码成带引号的字符串。
                .bodyValue(command.path("body"))
                .retrieve()
                .toBodilessEntity()
                .block(properties.getCallbackTimeout());
        reporter.progress(90, "OA 已确认接收回调");
        return "OA 回调成功";
    }
}
