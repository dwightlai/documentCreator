package cn.gov.document.platform.document.onlyoffice;

import cn.gov.document.platform.common.error.BusinessException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 与文档服务器共享密钥的 HS256 令牌：签名编辑器配置、平台自用的文件与回调访问令牌，校验文档服务器回调。 */
@Component
public class OnlyOfficeTokens {
    private final OnlyOfficeSettings settings;

    public OnlyOfficeTokens(OnlyOfficeSettings settings) {
        this.settings = settings;
    }

    public String sign(Map<String, Object> claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.parse(claims));
            jwt.sign(new MACSigner(settings.secret()));
            return jwt.serialize();
        } catch (JOSEException | ParseException failure) {
            throw new IllegalStateException("ONLYOFFICE 令牌签名失败", failure);
        }
    }

    /** 校验签名与有效期（带 exp 时），返回载荷；无效时 401 语义的业务异常。 */
    public Map<String, Object> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(settings.secret())))
                throw BusinessException.unprocessable("ONLYOFFICE 令牌签名无效");
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expires = claims.getExpirationTime();
            if (expires != null && expires.toInstant().isBefore(Instant.now()))
                throw BusinessException.unprocessable("ONLYOFFICE 令牌已过期");
            return claims.toJSONObject();
        } catch (ParseException | JOSEException invalid) {
            throw BusinessException.unprocessable("ONLYOFFICE 令牌格式无效");
        }
    }
}
