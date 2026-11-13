package cn.gov.document.platform.identity;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthSessionService sessions;

    public AuthController(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    public record LoginInput(@NotBlank String tenantCode, @NotBlank String username, @NotBlank String password) {}

    public record PasswordInput(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record TenantInput(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "租户编码只能使用小写字母、数字和连字符，且不超过 32 位")
                    String tenantCode,
            @NotBlank String tenantName,
            @NotBlank String adminUsername,
            @NotBlank String adminDisplayName,
            @NotBlank String adminPassword) {}

    public record TenantUpdate(@NotBlank String tenantName, boolean enabled) {}

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginInput input) {
        return ApiResponse.success(sessions.login(input.tenantCode(), input.username(), input.password()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        sessions.logout(bearer(request));
        return ApiResponse.success(null);
    }

    @PostMapping("/password")
    public ApiResponse<Void> password(@Valid @RequestBody PasswordInput input) {
        sessions.changePassword(
                RequestContext.tenantId(), RequestContext.userId(), input.currentPassword(), input.newPassword());
        return ApiResponse.success(null);
    }

    @GetMapping("/tenants")
    public ApiResponse<List<Map<String, Object>>> tenants() {
        return ApiResponse.success(sessions.tenants());
    }

    @GetMapping("/tenants/managed")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<List<Map<String, Object>>> managedTenants() {
        return ApiResponse.success(sessions.managedTenants());
    }

    @PostMapping("/tenants")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Map<String, Object>> createTenant(@Valid @RequestBody TenantInput input) {
        return ApiResponse.success(sessions.createTenant(
                input.tenantCode(),
                input.tenantName(),
                input.adminUsername(),
                input.adminDisplayName(),
                input.adminPassword()));
    }

    @PutMapping("/tenants/{code}")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<Map<String, Object>> updateTenant(
            @PathVariable String code, @Valid @RequestBody TenantUpdate input) {
        return ApiResponse.success(
                sessions.updateTenant(code, input.tenantName(), input.enabled(), RequestContext.tenantId()));
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return header.substring(7).trim();
    }
}
