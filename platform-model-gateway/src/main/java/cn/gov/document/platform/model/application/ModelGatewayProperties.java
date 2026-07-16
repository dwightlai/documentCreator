package cn.gov.document.platform.model.application;
import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.stereotype.Component;import java.util.ArrayList;import java.util.List;
@Component @ConfigurationProperties(prefix="platform.model") public class ModelGatewayProperties {
    private List<String> allowedEndpoints=new ArrayList<>();private boolean allowHttpLocal;
    public List<String> getAllowedEndpoints(){return allowedEndpoints;}public void setAllowedEndpoints(List<String> v){allowedEndpoints=v==null?new ArrayList<>():v;}
    public boolean isAllowHttpLocal(){return allowHttpLocal;}public void setAllowHttpLocal(boolean v){allowHttpLocal=v;}
}
