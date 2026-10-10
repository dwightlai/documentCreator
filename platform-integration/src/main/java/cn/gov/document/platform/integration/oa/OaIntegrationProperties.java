package cn.gov.document.platform.integration.oa;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.integration.oa")
public class OaIntegrationProperties {
    private List<String> callbackAllowlist = new ArrayList<>();
    private String sharedSecret;
    private Duration callbackTimeout = Duration.ofSeconds(10);

    public List<String> getCallbackAllowlist() {
        return callbackAllowlist;
    }

    public void setCallbackAllowlist(List<String> callbackAllowlist) {
        this.callbackAllowlist = callbackAllowlist;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public Duration getCallbackTimeout() {
        return callbackTimeout;
    }

    public void setCallbackTimeout(Duration callbackTimeout) {
        this.callbackTimeout = callbackTimeout;
    }
}
