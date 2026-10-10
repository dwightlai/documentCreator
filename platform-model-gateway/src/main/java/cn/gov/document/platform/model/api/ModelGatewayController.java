package cn.gov.document.platform.model.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.model.application.ModelGatewayService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModelGatewayController {
    private final ModelGatewayService service;

    public ModelGatewayController(ModelGatewayService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/model-configs")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<List<ModelDtos.ConfigResponse>> configs(@RequestParam(required = false) String scene) {
        return ApiResponse.success(service.configs(scene));
    }

    @PostMapping("/api/v1/model-configs")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<ModelDtos.ConfigResponse> create(@Valid @RequestBody ModelDtos.ConfigRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/api/v1/model-configs/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<ModelDtos.ConfigResponse> update(
            @PathVariable String id, @Valid @RequestBody ModelDtos.ConfigRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @GetMapping("/api/v1/model-call-logs")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<List<ModelDtos.LogResponse>> logs(
            @RequestParam(required = false) String taskType, @RequestParam(required = false) String taskId) {
        return ApiResponse.success(service.logs(taskType, taskId));
    }

    @PostMapping("/api/v1/model-gateway/route-preview")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<ModelDtos.RoutePreviewResponse> preview(@Valid @RequestBody ModelDtos.RoutePreviewRequest request) {
        return ApiResponse.success(service.prepare(request));
    }

    @PostMapping("/api/v1/model-gateway/invoke")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<ModelDtos.InvokeResponse> invoke(@Valid @RequestBody ModelDtos.InvokeRequest request) {
        return ApiResponse.success(service.invoke(request));
    }
}
