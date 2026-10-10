package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.common.error.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OaCallbackSecurity {
    private final OaIntegrationProperties properties;

    public OaCallbackSecurity(OaIntegrationProperties properties) {
        this.properties = properties;
    }

    public URI validateCallbackUrl(String callbackUrl) {
        if (!StringUtils.hasText(callbackUrl)) {
            throw BusinessException.unprocessable("OA 回调地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(callbackUrl).normalize();
        } catch (IllegalArgumentException invalidUri) {
            throw BusinessException.unprocessable("OA 回调地址格式无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw BusinessException.unprocessable("OA 回调地址必须是白名单内的 HTTPS 地址");
        }
        boolean allowed = properties.getCallbackAllowlist().stream()
                .filter(StringUtils::hasText)
                .map(value -> URI.create(value).normalize())
                .anyMatch(prefix -> sameOrigin(prefix, uri) && pathAllowed(prefix.getPath(), uri.getPath()));
        if (!allowed) {
            throw BusinessException.unprocessable("OA 回调地址未命中管理员配置的白名单");
        }
        if (!StringUtils.hasText(properties.getSharedSecret())) {
            throw BusinessException.unprocessable("OA 回调共享密钥尚未通过部署环境配置");
        }
        return uri;
    }

    public String sign(String timestamp, String nonce, String body) {
        if (!StringUtils.hasText(properties.getSharedSecret())) {
            throw new IllegalStateException("OA callback shared secret is not configured");
        }
        String canonical = timestamp + "\n" + nonce + "\n" + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", impossible);
        }
    }

    private boolean sameOrigin(URI allowed, URI actual) {
        int allowedPort = allowed.getPort() < 0 ? 443 : allowed.getPort();
        int actualPort = actual.getPort() < 0 ? 443 : actual.getPort();
        return "https".equalsIgnoreCase(allowed.getScheme())
                && allowed.getHost().equalsIgnoreCase(actual.getHost())
                && allowedPort == actualPort;
    }

    private boolean pathAllowed(String allowedPath, String actualPath) {
        String prefix = StringUtils.hasText(allowedPath) ? allowedPath : "/";
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        String path = StringUtils.hasText(actualPath) ? actualPath : "/";
        return path.equals(prefix.substring(0, prefix.length() - 1)) || path.startsWith(prefix);
    }
}
