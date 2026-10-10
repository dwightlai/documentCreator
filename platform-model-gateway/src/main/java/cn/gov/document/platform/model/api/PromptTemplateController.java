package cn.gov.document.platform.model.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.model.application.PromptTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prompt-templates")
@PreAuthorize("hasRole('platform.admin')")
public class PromptTemplateController {
    private final PromptTemplateService service;

    public PromptTemplateController(PromptTemplateService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<PromptTemplateDtos.Response>> list(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String documentTypeCode,
            @RequestParam(required = false) String lifecycleStatus) {
        return ApiResponse.success(service.list(scene, documentTypeCode, lifecycleStatus));
    }

    @GetMapping("/{id}")
    ApiResponse<PromptTemplateDtos.Response> get(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    ApiResponse<PromptTemplateDtos.Response> create(@Valid @RequestBody PromptTemplateDtos.Request r) {
        return ApiResponse.success(service.create(r));
    }

    @PutMapping("/{id}")
    ApiResponse<PromptTemplateDtos.Response> update(
            @PathVariable String id, @Valid @RequestBody PromptTemplateDtos.Request r) {
        return ApiResponse.success(service.update(id, r));
    }

    @PostMapping("/{id}/test")
    ApiResponse<PromptTemplateDtos.TestResponse> test(
            @PathVariable String id, @Valid @RequestBody PromptTemplateDtos.TestRequest r) {
        return ApiResponse.success(service.test(id, r));
    }

    @PostMapping("/{id}/submit-for-review")
    ApiResponse<PromptTemplateDtos.Response> submit(@PathVariable String id) {
        return ApiResponse.success(service.submit(id));
    }

    @PostMapping("/{id}/publish")
    ApiResponse<PromptTemplateDtos.Response> publish(@PathVariable String id) {
        return ApiResponse.success(service.publish(id));
    }

    @PatchMapping("/{id}/deactivate")
    ApiResponse<PromptTemplateDtos.Response> deactivate(@PathVariable String id) {
        return ApiResponse.success(service.deactivate(id));
    }

    @GetMapping("/{id}/versions")
    ApiResponse<List<PromptTemplateDtos.VersionResponse>> versions(@PathVariable String id) {
        return ApiResponse.success(service.versions(id));
    }
}
