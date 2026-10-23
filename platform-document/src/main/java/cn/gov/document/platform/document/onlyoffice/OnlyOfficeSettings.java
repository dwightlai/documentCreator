package cn.gov.document.platform.document.onlyoffice;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ONLYOFFICE Docs 定稿在线编辑适配（技术选型：像素级 DOCX 编辑通过适配层集成，不与业务组件耦合）。默认关闭；
 * 启用时必须配置浏览器访问地址、文档服务器回调平台的地址和至少 32 字节的 JWT 密钥（与文档服务器 JWT_SECRET 一致）。
 *
 * @param publicUrl 浏览器访问文档服务器的地址（推荐经前端 nginx 同源代理，如 https://平台域名/office）
 * @param internalUrl 平台后端访问文档服务器的地址，用于下载保存后的文件；为空时同 publicUrl
 * @param callbackBaseUrl 文档服务器访问平台后端的地址（拉取文件、保存回调）
 */
@Component
public class OnlyOfficeSettings {
    private final boolean enabled;
    private final String publicUrl;
    private final String internalUrl;
    private final String callbackBaseUrl;
    private final byte[] secret;

    public OnlyOfficeSettings(
            @Value("${platform.onlyoffice.enabled:false}") boolean enabled,
            @Value("${platform.onlyoffice.public-url:}") String publicUrl,
            @Value("${platform.onlyoffice.internal-url:}") String internalUrl,
            @Value("${platform.onlyoffice.callback-base-url:}") String callbackBaseUrl,
            @Value("${platform.onlyoffice.jwt-secret:}") String jwtSecret) {
        this.enabled = enabled;
        this.publicUrl = trim(publicUrl);
        this.internalUrl = StringUtils.hasText(internalUrl) ? trim(internalUrl) : this.publicUrl;
        this.callbackBaseUrl = trim(callbackBaseUrl);
        this.secret = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (enabled) {
            if (secret.length < 32) throw new IllegalStateException("platform.onlyoffice.jwt-secret 至少 32 字节");
            requireUrl("platform.onlyoffice.public-url", this.publicUrl);
            requireUrl("platform.onlyoffice.internal-url", this.internalUrl);
            requireUrl("platform.onlyoffice.callback-base-url", this.callbackBaseUrl);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String publicUrl() {
        return publicUrl;
    }

    public String internalUrl() {
        return internalUrl;
    }

    public String callbackBaseUrl() {
        return callbackBaseUrl;
    }

    byte[] secret() {
        return secret;
    }

    private static String trim(String value) {
        if (value == null) return "";
        String result = value.strip();
        return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }

    private static void requireUrl(String name, String value) {
        if (!StringUtils.hasText(value)) throw new IllegalStateException(name + " 未配置");
        URI uri = URI.create(value);
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalStateException(name + " 必须是 http(s) 地址");
    }
}
