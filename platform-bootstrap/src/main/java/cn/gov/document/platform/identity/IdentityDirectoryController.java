package cn.gov.document.platform.identity;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityDirectoryController {
    private final JdbcTemplate jdbc;
    private final AuthSessionService sessions;

    public IdentityDirectoryController(JdbcTemplate jdbc, AuthSessionService sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    public record OrganizationInput(
            @NotBlank String orgCode, @NotBlank String orgName, String parentId, boolean enabled) {}

    public record RoleInput(
            @NotBlank String roleCode, @NotBlank String roleName, String description, boolean enabled) {}

    public record UserInput(
            @NotBlank String username,
            @NotBlank String displayName,
            String orgId,
            boolean enabled,
            List<String> roleIds,
            String password) {}

    @GetMapping("/organizations")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<List<Map<String, Object>>> organizations() {
        return ApiResponse.success(jdbc.queryForList(
                "SELECT id, org_code, org_name, parent_id, enabled FROM platform_organization WHERE tenant_id=? ORDER BY org_name",
                tenant()));
    }

    @PostMapping("/organizations")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Map<String, Object>> createOrganization(@Valid @RequestBody OrganizationInput input) {
        unique("platform_organization", "org_code", input.orgCode(), null);
        validateParent(input.parentId(), null);
        String id = id();
        jdbc.update(
                "INSERT INTO platform_organization (id,tenant_id,org_code,org_name,parent_id,enabled,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                input.orgCode().trim(),
                input.orgName().trim(),
                blankToNull(input.parentId()),
                input.enabled(),
                now(),
                now());
        return ApiResponse.success(organization(id));
    }

    @PutMapping("/organizations/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Map<String, Object>> updateOrganization(
            @PathVariable String id, @Valid @RequestBody OrganizationInput input) {
        organization(id);
        unique("platform_organization", "org_code", input.orgCode(), id);
        validateParent(input.parentId(), id);
        jdbc.update(
                "UPDATE platform_organization SET org_code=?,org_name=?,parent_id=?,enabled=?,updated_at=? WHERE id=? AND tenant_id=?",
                input.orgCode().trim(),
                input.orgName().trim(),
                blankToNull(input.parentId()),
                input.enabled(),
                now(),
                id,
                tenant());
        return ApiResponse.success(organization(id));
    }

    @DeleteMapping("/organizations/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Void> deleteOrganization(@PathVariable String id) {
        organization(id);
        if (count("SELECT COUNT(*) FROM platform_user WHERE tenant_id=? AND org_id=?", id) > 0
                || count("SELECT COUNT(*) FROM platform_organization WHERE tenant_id=? AND parent_id=?", id) > 0) {
            throw BusinessException.conflict("组织下仍有用户或子组织");
        }
        jdbc.update("DELETE FROM platform_organization WHERE tenant_id=? AND id=?", tenant(), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.success(jdbc.queryForList(
                "SELECT id, role_code, role_name, description, enabled FROM platform_role WHERE tenant_id=? ORDER BY role_name",
                tenant()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Map<String, Object>> createRole(@Valid @RequestBody RoleInput input) {
        unique("platform_role", "role_code", input.roleCode(), null);
        String id = id();
        jdbc.update(
                "INSERT INTO platform_role (id,tenant_id,role_code,role_name,description,enabled,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                input.roleCode().trim(),
                input.roleName().trim(),
                input.description(),
                input.enabled(),
                now(),
                now());
        return ApiResponse.success(role(id));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable String id, @Valid @RequestBody RoleInput input) {
        var existing = role(id);
        if ("platform.admin".equals(existing.get("role_code"))
                && (!input.enabled() || !"platform.admin".equals(input.roleCode()))) {
            throw BusinessException.conflict("管理员角色不能停用或更改编码");
        }
        unique("platform_role", "role_code", input.roleCode(), id);
        jdbc.update(
                "UPDATE platform_role SET role_code=?,role_name=?,description=?,enabled=?,updated_at=? WHERE id=? AND tenant_id=?",
                input.roleCode().trim(),
                input.roleName().trim(),
                input.description(),
                input.enabled(),
                now(),
                id,
                tenant());
        return ApiResponse.success(role(id));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Void> deleteRole(@PathVariable String id) {
        role(id);
        if (count("SELECT COUNT(*) FROM platform_user_role WHERE tenant_id=? AND role_id=?", id) > 0) {
            throw BusinessException.conflict("角色仍分配给用户");
        }
        jdbc.update("DELETE FROM platform_role WHERE tenant_id=? AND id=?", tenant(), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<List<Map<String, Object>>> users() {
        var users = jdbc.queryForList(
                "SELECT id,username,display_name,org_id,enabled FROM platform_user WHERE tenant_id=? ORDER BY display_name",
                tenant());
        users.forEach(user -> user.put("roleIds", roleIds((String) user.get("id"))));
        return ApiResponse.success(users);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        var users = jdbc.queryForList(
                "SELECT id,username,display_name,org_id,enabled FROM platform_user WHERE tenant_id=? AND username=?",
                tenant(),
                RequestContext.userId());
        if (users.isEmpty())
            return ApiResponse.success(Map.of(
                    "username",
                    RequestContext.userId(),
                    "displayName",
                    RequestContext.userId(),
                    "tenantCode",
                    tenant(),
                    "roles",
                    effectiveRoles()));
        var user = users.get(0);
        if (user.get("org_id") != null) {
            var orgs = jdbc.queryForList(
                    "SELECT org_name FROM platform_organization WHERE tenant_id=? AND id=?",
                    tenant(),
                    user.get("org_id"));
            if (!orgs.isEmpty()) user.put("orgName", orgs.get(0).get("org_name"));
        }
        user.put("tenantCode", tenant());
        user.put("roles", effectiveRoles());
        return ApiResponse.success(user);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Map<String, Object>> createUser(@Valid @RequestBody UserInput input) {
        unique("platform_user", "username", input.username(), null);
        validateMembership(input);
        String id = id();
        jdbc.update(
                "INSERT INTO platform_user (id,tenant_id,username,display_name,org_id,enabled,password_hash,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                input.username().trim(),
                input.displayName().trim(),
                blankToNull(input.orgId()),
                input.enabled(),
                sessions.hashPassword(input.password()),
                now(),
                now());
        saveRoles(id, input.roleIds());
        return ApiResponse.success(user(id));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable String id, @Valid @RequestBody UserInput input) {
        var existing = user(id);
        unique("platform_user", "username", input.username(), id);
        if (RequestContext.userId().equals(existing.get("username"))
                && (!input.enabled()
                        || !input.username().equals(existing.get("username"))
                        || !hasAdminRole(input.roleIds()))) {
            throw BusinessException.conflict("不能停用、重命名自己或移除自己的管理员角色");
        }
        validateMembership(input);
        jdbc.update(
                "UPDATE platform_user SET username=?,display_name=?,org_id=?,enabled=?,updated_at=? WHERE id=? AND tenant_id=?",
                input.username().trim(),
                input.displayName().trim(),
                blankToNull(input.orgId()),
                input.enabled(),
                now(),
                id,
                tenant());
        if (input.password() != null && !input.password().isBlank()) {
            jdbc.update(
                    "UPDATE platform_user SET password_hash=? WHERE id=? AND tenant_id=?",
                    sessions.hashPassword(input.password()),
                    id,
                    tenant());
            sessions.revoke(tenant(), id);
        }
        if (!input.enabled()) sessions.revoke(tenant(), id);
        jdbc.update("DELETE FROM platform_user_role WHERE tenant_id=? AND user_id=?", tenant(), id);
        saveRoles(id, input.roleIds());
        return ApiResponse.success(user(id));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        var existing = user(id);
        if (RequestContext.userId().equals(existing.get("username"))) throw BusinessException.conflict("不能删除当前登录用户");
        sessions.revoke(tenant(), id);
        jdbc.update("DELETE FROM platform_user_role WHERE tenant_id=? AND user_id=?", tenant(), id);
        jdbc.update("DELETE FROM platform_user WHERE tenant_id=? AND id=?", tenant(), id);
        return ApiResponse.success(null);
    }

    private void validateMembership(UserInput input) {
        if (blankToNull(input.orgId()) != null) organization(input.orgId());
        if (input.roleIds() != null) input.roleIds().forEach(this::role);
    }

    private void validateParent(String parentId, String id) {
        if (blankToNull(parentId) == null) return;
        if (parentId.equals(id)) throw BusinessException.unprocessable("组织不能设为自己的上级");
        organization(parentId);
        String cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(id)) throw BusinessException.unprocessable("组织层级不能形成循环");
            cursor = (String) organization(cursor).get("parent_id");
        }
    }

    private void saveRoles(String userId, List<String> roleIds) {
        if (roleIds == null) return;
        for (String roleId : roleIds.stream().distinct().toList()) {
            jdbc.update(
                    "INSERT INTO platform_user_role (tenant_id,user_id,role_id) VALUES (?,?,?)",
                    tenant(),
                    userId,
                    roleId);
        }
    }

    private boolean hasAdminRole(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return false;
        return roleIds.stream().anyMatch(id -> "platform.admin".equals(role(id).get("role_code")));
    }

    private List<String> effectiveRoles() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                ? List.of()
                : authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5))
                        .distinct()
                        .toList();
    }

    private void unique(String table, String column, String value, String excludingId) {
        // table/column are fixed server-side names, never request input.
        Integer matches = excludingId == null
                ? jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND " + column + "=?",
                        Integer.class,
                        tenant(),
                        value.trim())
                : jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND " + column + "=? AND id<>?",
                        Integer.class,
                        tenant(),
                        value.trim(),
                        excludingId);
        if (matches != null && matches > 0) throw BusinessException.conflict("编码或账号已存在");
    }

    private List<String> roleIds(String userId) {
        return jdbc.queryForList(
                "SELECT role_id FROM platform_user_role WHERE tenant_id=? AND user_id=?",
                String.class,
                tenant(),
                userId);
    }

    private Map<String, Object> organization(String id) {
        return one(
                "SELECT id,org_code,org_name,parent_id,enabled FROM platform_organization WHERE tenant_id=? AND id=?",
                id);
    }

    private Map<String, Object> role(String id) {
        return one(
                "SELECT id,role_code,role_name,description,enabled FROM platform_role WHERE tenant_id=? AND id=?", id);
    }

    private Map<String, Object> user(String id) {
        var result =
                one("SELECT id,username,display_name,org_id,enabled FROM platform_user WHERE tenant_id=? AND id=?", id);
        result.put("roleIds", roleIds(id));
        return result;
    }

    private Map<String, Object> one(String sql, String id) {
        var rows = jdbc.queryForList(sql, tenant(), id);
        if (rows.isEmpty()) throw BusinessException.notFound("记录不存在");
        return rows.get(0);
    }

    private int count(String sql, String id) {
        return jdbc.queryForObject(sql, Integer.class, tenant(), id);
    }

    private static String tenant() {
        return RequestContext.tenantId();
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
