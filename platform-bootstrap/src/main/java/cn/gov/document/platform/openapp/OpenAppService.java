package cn.gov.document.platform.openapp;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenAppService {
    public static final Set<String> CAPABILITIES = Set.of("writing", "summary", "polish", "expand", "review");
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public OpenAppService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT id, app_code, app_name, app_type, auth_type, status, created_at FROM open_application WHERE tenant_id=? ORDER BY created_at DESC",
                tenant());
    }

    public Map<String, Object> detail(String id) {
        Map<String, Object> app = one(id);
        app.put(
                "keys",
                jdbc.queryForList(
                        "SELECT id, key_prefix, status, created_at FROM open_application_key WHERE tenant_id=? AND app_id=? ORDER BY created_at DESC",
                        tenant(),
                        id));
        app.put(
                "grants",
                jdbc.queryForList(
                        "SELECT id, scope_type, scope_code, created_at FROM open_application_grant WHERE tenant_id=? AND app_id=? ORDER BY created_at",
                        tenant(),
                        id));
        return app;
    }

    @Transactional
    public Map<String, Object> create(String appCode, String appName) {
        String code = normalizeCode(appCode);
        if (!org.springframework.util.StringUtils.hasText(appName)
                || appName.trim().length() > 100) throw BusinessException.unprocessable("请填写应用名称");
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM open_application WHERE tenant_id=? AND app_code=?",
                Integer.class,
                tenant(),
                code);
        if (exists != null && exists > 0) throw BusinessException.conflict("应用编码已存在");
        String id = id();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                "INSERT INTO open_application (id,tenant_id,app_code,app_name,app_type,auth_type,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                code,
                appName.trim(),
                "BUSINESS_SYSTEM",
                "API_KEY",
                "ACTIVE",
                now,
                now);
        return one(id);
    }

    @Transactional
    public Map<String, Object> setEnabled(String id, boolean enabled) {
        one(id);
        jdbc.update(
                "UPDATE open_application SET status=?, updated_at=? WHERE tenant_id=? AND id=?",
                enabled ? "ACTIVE" : "DISABLED",
                OffsetDateTime.now(),
                tenant(),
                id);
        return one(id);
    }

    @Transactional
    public void delete(String id) {
        one(id);
        jdbc.update("DELETE FROM open_application_grant WHERE tenant_id=? AND app_id=?", tenant(), id);
        jdbc.update("DELETE FROM open_application_key WHERE tenant_id=? AND app_id=?", tenant(), id);
        jdbc.update("DELETE FROM open_application WHERE tenant_id=? AND id=?", tenant(), id);
    }

    @Transactional
    public Map<String, Object> createKey(String appId) {
        one(appId);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String apiKey = "doc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = id();
        jdbc.update(
                "INSERT INTO open_application_key (id,tenant_id,app_id,key_prefix,key_hash,status,created_at) VALUES (?,?,?,?,?,?,?)",
                id,
                tenant(),
                appId,
                apiKey.substring(0, 12),
                digest(apiKey),
                "ACTIVE",
                OffsetDateTime.now());
        return Map.of("id", id, "keyPrefix", apiKey.substring(0, 12), "apiKey", apiKey);
    }

    @Transactional
    public void deleteKey(String appId, String keyId) {
        one(appId);
        int rows = jdbc.update(
                "DELETE FROM open_application_key WHERE tenant_id=? AND app_id=? AND id=?", tenant(), appId, keyId);
        if (rows == 0) throw BusinessException.notFound("密钥不存在");
    }

    @Transactional
    public Map<String, Object> addGrant(String appId, String scopeType, String scopeCode) {
        one(appId);
        String type = scopeType == null ? "" : scopeType.trim();
        String code = scopeCode == null ? "" : scopeCode.trim();
        if ("TENANT".equals(type)) code = "";
        else if ("CAPABILITY".equals(type) && CAPABILITIES.contains(code)) {
            /* ok */
        } else throw BusinessException.unprocessable("请选择可调用范围");
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM open_application_grant WHERE tenant_id=? AND app_id=? AND scope_type=? AND scope_code=?",
                Integer.class,
                tenant(),
                appId,
                type,
                code);
        if (exists != null && exists > 0) throw BusinessException.conflict("该授权已存在");
        String id = id();
        jdbc.update(
                "INSERT INTO open_application_grant (id,tenant_id,app_id,scope_type,scope_code,created_at) VALUES (?,?,?,?,?,?)",
                id,
                tenant(),
                appId,
                type,
                code,
                OffsetDateTime.now());
        return Map.of("id", id, "scopeType", type, "scopeCode", code);
    }

    @Transactional
    public void deleteGrant(String appId, String grantId) {
        one(appId);
        int rows = jdbc.update(
                "DELETE FROM open_application_grant WHERE tenant_id=? AND app_id=? AND id=?", tenant(), appId, grantId);
        if (rows == 0) throw BusinessException.notFound("授权不存在");
    }

    public AuthDecision authenticate(
            String appCode,
            String apiKey,
            String capability,
            String externalUser,
            String unitId,
            String departmentId,
            String roleIds,
            String path,
            String ip) {
        if (appCode == null || apiKey == null || apiKey.length() > 200) return AuthDecision.reject(401, "应用编码或密钥无效");
        String code = appCode.trim().toUpperCase();
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT a.id, a.tenant_id, a.status AS app_status, k.status AS key_status
                FROM open_application_key k
                JOIN open_application a ON a.id=k.app_id AND a.tenant_id=k.tenant_id
                JOIN platform_tenant t ON t.tenant_code=a.tenant_id
                WHERE k.key_hash=? AND a.app_code=? AND t.enabled=TRUE
                """,
                digest(apiKey),
                code);
        if (rows.isEmpty()) return AuthDecision.reject(401, "应用编码或密钥无效");
        Map<String, Object> row = rows.get(0);
        String appId = (String) row.get("id");
        String tenantId = (String) row.get("tenant_id");
        if (!"ACTIVE".equals(row.get("app_status")) || !"ACTIVE".equals(row.get("key_status")))
            return AuthDecision.reject(401, "应用已停用");
        if (capability == null || !allowed(tenantId, appId, capability)) {
            audit(tenantId, appId, "DENIED", externalUser, unitId, departmentId, roleIds, path, ip);
            return AuthDecision.reject(403, "该应用未获授权调用此接口");
        }
        audit(tenantId, appId, capability, externalUser, unitId, departmentId, roleIds, path, ip);
        return AuthDecision.ok(tenantId, appId);
    }

    public static String capability(String path) {
        if (path.contains("/documents/summary")) return "summary";
        if (path.contains("/documents/polish")) return "polish";
        if (path.contains("/documents/expand")) return "expand";
        if (path.contains("/documents/review") || path.contains("/review-tasks")) return "review";
        if (path.contains("/writing-tasks")) return "writing";
        return null;
    }

    private boolean allowed(String tenantId, String appId, String capability) {
        Integer tenantScope = jdbc.queryForObject(
                "SELECT COUNT(*) FROM open_application_grant WHERE tenant_id=? AND app_id=? AND scope_type='TENANT'",
                Integer.class,
                tenantId,
                appId);
        if (tenantScope != null && tenantScope > 0) return true;
        Integer capabilityScope = jdbc.queryForObject(
                "SELECT COUNT(*) FROM open_application_grant WHERE tenant_id=? AND app_id=? AND scope_type='CAPABILITY' AND scope_code=?",
                Integer.class,
                tenantId,
                appId,
                capability);
        return capabilityScope != null && capabilityScope > 0;
    }

    private void audit(
            String tenantId,
            String appId,
            String action,
            String user,
            String unit,
            String department,
            String roles,
            String path,
            String ip) {
        String detail = "user=" + clip(user, 100) + ";unit=" + clip(unit, 100) + ";department=" + clip(department, 100)
                + ";path=" + clip(path, 200);
        jdbc.update(
                "INSERT INTO ai_audit_log (id,tenant_id,audit_type,operator_id,operator_role,target_type,target_id,action,detail,ip_address,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id(),
                tenantId,
                "OPEN_API",
                appId,
                clip(roles, 500),
                "OPEN_APP",
                appId,
                clip(action, 30),
                detail,
                clip(ip, 50),
                OffsetDateTime.now());
    }

    private Map<String, Object> one(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, app_code, app_name, app_type, auth_type, status, created_at FROM open_application WHERE tenant_id=? AND id=?",
                tenant(),
                id);
        if (rows.isEmpty()) throw BusinessException.notFound("应用不存在");
        return rows.get(0);
    }

    private static String normalizeCode(String appCode) {
        String code = appCode == null ? "" : appCode.trim().toUpperCase();
        if (!code.matches("[A-Z][A-Z0-9_-]{1,31}")) throw BusinessException.unprocessable("应用编码为 2-32 位字母、数字、下划线或中划线");
        return code;
    }

    private static String tenant() {
        return RequestContext.tenantId();
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record AuthDecision(boolean ok, int status, String message, String tenantId, String appId) {
        static AuthDecision ok(String tenantId, String appId) {
            return new AuthDecision(true, 200, "", tenantId, appId);
        }

        static AuthDecision reject(int status, String message) {
            return new AuthDecision(false, status, message, null, null);
        }
    }
}
