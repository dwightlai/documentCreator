package cn.gov.document.platform.identity;

import cn.gov.document.platform.common.context.RequestContextFilter.SessionIdentity;
import cn.gov.document.platform.common.error.BusinessException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {
    private static final int SESSION_HOURS = 12;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode("unused-password");
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SessionIdentity> resolve(String rawToken) {
        var rows = jdbc.queryForList(
                """
                SELECT s.tenant_id, u.username, u.id AS user_id
                FROM platform_session s
                JOIN platform_user u ON u.tenant_id=s.tenant_id AND u.id=s.user_id
                JOIN platform_tenant t ON t.tenant_code=s.tenant_id
                WHERE s.token_hash=? AND s.expires_at>? AND u.enabled=TRUE AND t.enabled=TRUE
                """,
                digest(rawToken),
                OffsetDateTime.now());
        if (rows.isEmpty()) return Optional.empty();
        String tenantId = (String) rows.get(0).get("tenant_id");
        String username = (String) rows.get(0).get("username");
        return Optional.of(new SessionIdentity(tenantId, username, roles(tenantId, username)));
    }

    @Transactional
    public Map<String, Object> login(String tenantCode, String username, String password) {
        jdbc.update("DELETE FROM platform_session WHERE expires_at<=?", OffsetDateTime.now());
        var users = jdbc.queryForList(
                """
                SELECT u.id, u.password_hash, u.display_name, t.tenant_name
                FROM platform_user u
                JOIN platform_tenant t ON t.tenant_code=u.tenant_id
                WHERE u.tenant_id=? AND u.username=? AND u.enabled=TRUE AND t.enabled=TRUE
                """,
                tenantCode.trim(),
                username.trim());
        String passwordHash = users.isEmpty() ? null : (String) users.get(0).get("password_hash");
        if (passwordHash == null || !encoder.matches(password, passwordHash)) {
            encoder.matches(password, dummyHash);
            throw BusinessException.unauthorized("账号或密码错误");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        OffsetDateTime expires = OffsetDateTime.now().plusHours(SESSION_HOURS);
        jdbc.update(
                "INSERT INTO platform_session (token_hash,tenant_id,user_id,expires_at,created_at) VALUES (?,?,?,?,?)",
                digest(token),
                tenantCode.trim(),
                users.get(0).get("id"),
                expires,
                OffsetDateTime.now());
        return Map.of(
                "token", token,
                "expiresAt", expires.toString(),
                "tenantCode", tenantCode.trim(),
                "tenantName", users.get(0).get("tenant_name"),
                "username", username.trim(),
                "displayName", users.get(0).get("display_name"),
                "roles", roles(tenantCode.trim(), username.trim()));
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        jdbc.update("DELETE FROM platform_session WHERE token_hash=?", digest(rawToken));
    }

    @Transactional
    public void changePassword(String tenantId, String username, String currentPassword, String newPassword) {
        requirePassword(newPassword);
        var users = jdbc.queryForList(
                "SELECT id, password_hash FROM platform_user WHERE tenant_id=? AND username=? AND enabled=TRUE",
                tenantId,
                username);
        Object stored = users.isEmpty() ? null : users.get(0).get("password_hash");
        if (!(stored instanceof String hash) || !encoder.matches(currentPassword, hash)) {
            encoder.matches(currentPassword, dummyHash);
            throw BusinessException.unauthorized("账号或密码错误");
        }
        jdbc.update(
                "UPDATE platform_user SET password_hash=?, updated_at=? WHERE tenant_id=? AND id=?",
                encoder.encode(newPassword),
                OffsetDateTime.now(),
                tenantId,
                users.get(0).get("id"));
        revoke(tenantId, (String) users.get(0).get("id"));
    }

    public List<Map<String, Object>> tenants() {
        return jdbc.queryForList(
                "SELECT tenant_code, tenant_name FROM platform_tenant WHERE enabled=TRUE ORDER BY tenant_name");
    }

    public List<Map<String, Object>> managedTenants() {
        return jdbc.queryForList("SELECT tenant_code, tenant_name, enabled FROM platform_tenant ORDER BY tenant_name");
    }

    @Transactional
    public Map<String, Object> updateTenant(String code, String name, boolean enabled, String currentTenant) {
        Integer existing =
                jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant WHERE tenant_code=?", Integer.class, code);
        if (existing == null || existing == 0) throw BusinessException.notFound("租户不存在");
        if (!enabled && code.equals(currentTenant)) throw BusinessException.conflict("不能停用当前登录租户");
        jdbc.update(
                "UPDATE platform_tenant SET tenant_name=?, enabled=?, updated_at=? WHERE tenant_code=?",
                name.trim(),
                enabled,
                OffsetDateTime.now(),
                code);
        if (!enabled) jdbc.update("DELETE FROM platform_session WHERE tenant_id=?", code);
        return Map.of("tenant_code", code, "tenant_name", name.trim(), "enabled", enabled);
    }

    @Transactional
    public Map<String, Object> createTenant(
            String tenantCode, String tenantName, String adminUsername, String adminDisplayName, String adminPassword) {
        requirePassword(adminPassword);
        String code = tenantCode.trim();
        Integer existing =
                jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant WHERE tenant_code=?", Integer.class, code);
        if (existing != null && existing > 0) throw BusinessException.conflict("租户编码已存在");
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                "INSERT INTO platform_tenant (id,tenant_code,tenant_name,enabled,created_at,updated_at) VALUES (?,?,?,TRUE,?,?)",
                id(),
                code,
                tenantName.trim(),
                now,
                now);
        String orgId = id();
        jdbc.update(
                "INSERT INTO platform_organization (id,tenant_id,org_code,org_name,parent_id,enabled,created_at,updated_at) VALUES (?,?,?,?,NULL,TRUE,?,?)",
                orgId,
                code,
                "general-office",
                "综合办公处",
                now,
                now);
        String adminRole = id();
        insertRole(adminRole, code, "platform.admin", "管理员", "管理平台配置和身份目录", now);
        insertRole(id(), code, "platform.reviewer", "核稿人员", "确认文稿和审校结果", now);
        insertRole(id(), code, "platform.user", "普通用户", "使用写作和审校功能", now);
        String userId = id();
        jdbc.update(
                "INSERT INTO platform_user (id,tenant_id,username,display_name,org_id,enabled,password_hash,created_at,updated_at) VALUES (?,?,?,?,?,TRUE,?,?,?)",
                userId,
                code,
                adminUsername.trim(),
                adminDisplayName.trim(),
                orgId,
                encoder.encode(adminPassword),
                now,
                now);
        jdbc.update(
                "INSERT INTO platform_user_role (tenant_id,user_id,role_id) VALUES (?,?,?)", code, userId, adminRole);
        return Map.of("tenantCode", code, "tenantName", tenantName.trim(), "username", adminUsername.trim());
    }

    public String hashPassword(String password) {
        if (password == null || password.isBlank()) return null;
        requirePassword(password);
        return encoder.encode(password);
    }

    public void revoke(String tenantId, String userId) {
        jdbc.update("DELETE FROM platform_session WHERE tenant_id=? AND user_id=?", tenantId, userId);
    }

    public void ensureSeedPassword(String password) {
        Integer missing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_user WHERE tenant_id='local-government' AND username='admin' AND password_hash IS NULL",
                Integer.class);
        if (missing != null && missing > 0) {
            jdbc.update(
                    "UPDATE platform_user SET password_hash=?, updated_at=? WHERE tenant_id='local-government' AND username='admin'",
                    encoder.encode(password),
                    OffsetDateTime.now());
        }
    }

    private void insertRole(
            String id, String tenantId, String code, String name, String description, OffsetDateTime now) {
        jdbc.update(
                "INSERT INTO platform_role (id,tenant_id,role_code,role_name,description,enabled,created_at,updated_at) VALUES (?,?,?,?,?,TRUE,?,?)",
                id,
                tenantId,
                code,
                name,
                description,
                now,
                now);
    }

    private List<String> roles(String tenantId, String username) {
        return jdbc.queryForList(
                """
                SELECT r.role_code FROM platform_user u
                JOIN platform_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
                JOIN platform_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
                WHERE u.tenant_id=? AND u.username=? AND u.enabled=TRUE AND r.enabled=TRUE
                """,
                String.class,
                tenantId,
                username);
    }

    private static void requirePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw BusinessException.unprocessable("密码长度须为 8 到 72 位");
        }
    }

    private static String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
