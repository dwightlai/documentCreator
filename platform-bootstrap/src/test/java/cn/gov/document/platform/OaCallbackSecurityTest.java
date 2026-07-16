package cn.gov.document.platform;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.integration.oa.OaCallbackSecurity;
import cn.gov.document.platform.integration.oa.OaIntegrationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OaCallbackSecurityTest {
    @Test
    void allowsOnlyConfiguredHttpsOriginAndPathAndSignsCanonicalBody() {
        OaIntegrationProperties properties = new OaIntegrationProperties();
        properties.setCallbackAllowlist(List.of("https://oa.example.gov.cn:8443/callback/review/"));
        properties.setSharedSecret("test-secret");
        OaCallbackSecurity security = new OaCallbackSecurity(properties);

        assertThat(security.validateCallbackUrl(
                "https://oa.example.gov.cn:8443/callback/review/finished?businessId=1").getHost())
                .isEqualTo("oa.example.gov.cn");
        assertThat(security.sign("1700000000", "abc123", "{\"status\":\"COMPLETED\"}"))
                .isEqualTo("baf6119d88559024d931ea5ba76e60060e0a15a817eb15f9b35f2ff269dbbff5");

        assertRejected(security, "http://oa.example.gov.cn:8443/callback/review/finished");
        assertRejected(security, "https://oa.example.gov.cn.evil.test:8443/callback/review/finished");
        assertRejected(security, "https://oa.example.gov.cn:8443/internal/metadata");
        assertRejected(security, "https://user@oa.example.gov.cn:8443/callback/review/finished");
    }

    private void assertRejected(OaCallbackSecurity security, String url) {
        assertThatThrownBy(() -> security.validateCallbackUrl(url))
                .isInstanceOf(BusinessException.class);
    }
}
