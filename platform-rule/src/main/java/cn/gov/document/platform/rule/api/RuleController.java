package cn.gov.document.platform.rule.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.rule.application.RuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {
    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<RuleDtos.Response>> list(
            @RequestParam(required = false) String category, @RequestParam(required = false) String lifecycleStatus) {
        return ApiResponse.success(service.list(category, lifecycleStatus));
    }

    @GetMapping("/{ruleId}")
    ApiResponse<RuleDtos.Response> get(@PathVariable String ruleId) {
        return ApiResponse.success(service.get(ruleId));
    }

    @PostMapping
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<RuleDtos.Response> create(@Valid @RequestBody RuleDtos.CreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<RuleDtos.Response> update(
            @PathVariable String ruleId, @Valid @RequestBody RuleDtos.UpdateRequest request) {
        return ApiResponse.success(service.update(ruleId, request));
    }

    @PostMapping("/{ruleId}/test")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<RuleDtos.TestResponse> test(
            @PathVariable String ruleId, @Valid @RequestBody RuleDtos.TestRequest request) {
        return ApiResponse.success(service.test(ruleId, request));
    }

    @PostMapping("/{ruleId}/submit-for-review")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<RuleDtos.Response> submit(@PathVariable String ruleId) {
        return ApiResponse.success(service.submit(ruleId));
    }

    @PostMapping("/{ruleId}/publish")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<RuleDtos.Response> publish(@PathVariable String ruleId) {
        return ApiResponse.success(service.publish(ruleId));
    }

    @PatchMapping("/{ruleId}/deactivate")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<RuleDtos.Response> deactivate(@PathVariable String ruleId) {
        return ApiResponse.success(service.deactivate(ruleId));
    }

    @GetMapping("/{ruleId}/versions")
    ApiResponse<List<RuleDtos.VersionResponse>> versions(@PathVariable String ruleId) {
        return ApiResponse.success(service.versions(ruleId));
    }
}
