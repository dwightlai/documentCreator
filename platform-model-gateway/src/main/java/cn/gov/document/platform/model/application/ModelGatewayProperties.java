package cn.gov.document.platform.model.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.model")
public class ModelGatewayProperties {
    private List<String> allowedEndpoints = new ArrayList<>();
    private boolean allowHttpLocal;

    public List<String> getAllowedEndpoints() {
        return allowedEndpoints;
    }

    public void setAllowedEndpoints(List<String> v) {
        allowedEndpoints = v == null ? new ArrayList<>() : v;
    }

    public boolean isAllowHttpLocal() {
        return allowHttpLocal;
    }

    public void setAllowHttpLocal(boolean v) {
        allowHttpLocal = v;
    }
}
