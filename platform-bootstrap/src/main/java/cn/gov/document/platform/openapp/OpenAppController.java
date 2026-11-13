package cn.gov.document.platform.openapp;

import cn.gov.document.platform.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open-apps")
@PreAuthorize("hasRole('platform.admin')")
public class OpenAppController {
    private final OpenAppService apps;

    public OpenAppController(OpenAppService apps) {
        this.apps = apps;
    }

    public record CreateRequest(@NotBlank String appCode, @NotBlank String appName) {}

    public record StatusRequest(boolean enabled) {}

    public record GrantRequest(@NotBlank String scopeType, String scopeCode) {}

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(apps.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.success(apps.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.success(apps.create(request.appCode(), request.appName()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable String id, @RequestBody StatusRequest request) {
        return ApiResponse.success(apps.setEnabled(id, request.enabled()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        apps.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/keys")
    public ApiResponse<Map<String, Object>> createKey(@PathVariable String id) {
        return ApiResponse.success(apps.createKey(id));
    }

    @DeleteMapping("/{id}/keys/{keyId}")
    public ApiResponse<Void> deleteKey(@PathVariable String id, @PathVariable String keyId) {
        apps.deleteKey(id, keyId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/grants")
    public ApiResponse<Map<String, Object>> addGrant(
            @PathVariable String id, @Valid @RequestBody GrantRequest request) {
        return ApiResponse.success(apps.addGrant(id, request.scopeType(), request.scopeCode()));
    }

    @DeleteMapping("/{id}/grants/{grantId}")
    public ApiResponse<Void> deleteGrant(@PathVariable String id, @PathVariable String grantId) {
        apps.deleteGrant(id, grantId);
        return ApiResponse.success(null);
    }
}
