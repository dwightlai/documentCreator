package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.error.BusinessException;
import java.net.*;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ModelEndpointSecurity {
    private final ModelGatewayProperties properties;

    public ModelEndpointSecurity(ModelGatewayProperties p) {
        properties = p;
    }

    public URI validate(String value) {
        if (value == null || value.isBlank()) throw BusinessException.unprocessable("模型配置未设置调用端点");
        try {
            URI uri = new URI(value).normalize();
            if (uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getQuery() != null
                    || uri.getHost() == null) throw BusinessException.unprocessable("模型调用端点格式不合法");
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean loopback = isLoopback(uri.getHost());
            if ("http".equals(scheme) && !(loopback && properties.isAllowHttpLocal()))
                throw BusinessException.unprocessable("模型调用端点必须使用 HTTPS");
            if (!"https".equals(scheme) && !"http".equals(scheme))
                throw BusinessException.unprocessable("模型调用端点协议不受支持");
            String exact = originAndPath(uri);
            boolean allowed = loopback && properties.isAllowHttpLocal()
                    || properties.getAllowedEndpoints().stream()
                            .map(this::normalizeAllowed)
                            .anyMatch(exact::equals);
            if (!allowed) throw BusinessException.unprocessable("模型调用端点不在白名单");
            return uri;
        } catch (URISyntaxException e) {
            throw BusinessException.unprocessable("模型调用端点格式不合法");
        }
    }

    private String originAndPath(URI u) {
        int port = u.getPort();
        String authority = u.getHost().toLowerCase(Locale.ROOT) + (port < 0 ? "" : ":" + port);
        return u.getScheme().toLowerCase(Locale.ROOT) + "://" + authority + (u.getPath() == null ? "" : u.getPath());
    }

    private String normalizeAllowed(String v) {
        try {
            return originAndPath(new URI(v).normalize());
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
