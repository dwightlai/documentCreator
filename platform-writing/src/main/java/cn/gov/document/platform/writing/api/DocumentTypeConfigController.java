package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.writing.application.DocumentTypeConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/document-type-configs")
public class DocumentTypeConfigController {
    private final DocumentTypeConfigService service;

    public DocumentTypeConfigController(DocumentTypeConfigService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<DocumentTypeConfigDtos.Response>> list(
            @RequestParam(required = false) String documentCategory,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String lifecycleStatus) {
        return ApiResponse.success(service.list(documentCategory, direction, lifecycleStatus));
    }

    @GetMapping("/{typeCode}")
    ApiResponse<DocumentTypeConfigDtos.Response> get(@PathVariable String typeCode) {
        return ApiResponse.success(service.get(typeCode));
    }

    @PostMapping
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocumentTypeConfigDtos.Response> create(@Valid @RequestBody DocumentTypeConfigDtos.CreateRequest r) {
        return ApiResponse.success(service.create(r));
    }

    @PutMapping("/{typeCode}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocumentTypeConfigDtos.Response> update(
            @PathVariable String typeCode, @Valid @RequestBody DocumentTypeConfigDtos.UpdateRequest r) {
        return ApiResponse.success(service.update(typeCode, r));
    }

    @PostMapping("/{typeCode}/test")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocumentTypeConfigDtos.TestResponse> test(@PathVariable String typeCode) {
        return ApiResponse.success(service.test(typeCode));
    }

    @PostMapping("/{typeCode}/submit-for-review")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocumentTypeConfigDtos.Response> submit(@PathVariable String typeCode) {
        return ApiResponse.success(service.submit(typeCode));
    }

    @PostMapping("/{typeCode}/publish")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocumentTypeConfigDtos.Response> publish(@PathVariable String typeCode) {
        return ApiResponse.success(service.publish(typeCode));
    }

    @PatchMapping("/{typeCode}/deactivate")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocumentTypeConfigDtos.Response> deactivate(@PathVariable String typeCode) {
        return ApiResponse.success(service.deactivate(typeCode));
    }

    @GetMapping("/{typeCode}/versions")
    ApiResponse<List<DocumentTypeConfigDtos.VersionResponse>> versions(@PathVariable String typeCode) {
        return ApiResponse.success(service.versions(typeCode));
    }
}
