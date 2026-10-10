package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.api.PageResponse;
import cn.gov.document.platform.writing.application.WritingAssistant;
import cn.gov.document.platform.writing.application.WritingTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/writing-tasks")
public class WritingTaskController {
    private final WritingTaskService service;
    private final WritingAssistant assistant;

    public WritingTaskController(WritingTaskService service, WritingAssistant assistant) {
        this.service = service;
        this.assistant = assistant;
    }

    @PostMapping
    ApiResponse<WritingTaskDtos.Response> create(@Valid @RequestBody WritingTaskDtos.CreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/{taskId}")
    ApiResponse<WritingTaskDtos.Response> get(@PathVariable String taskId) {
        return ApiResponse.success(service.get(taskId));
    }

    @GetMapping
    ApiResponse<PageResponse<WritingTaskDtos.Response>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType) {
        return ApiResponse.success(service.list(page, size, status, documentType));
    }

    @PutMapping("/{taskId}")
    ApiResponse<WritingTaskDtos.Response> update(
            @PathVariable String taskId, @Valid @RequestBody WritingTaskDtos.UpdateRequest request) {
        return ApiResponse.success(service.update(taskId, request));
    }

    @PatchMapping("/{taskId}/status")
    ApiResponse<WritingTaskDtos.Response> status(
            @PathVariable String taskId, @Valid @RequestBody WritingTaskDtos.StatusRequest request) {
        return ApiResponse.success(service.changeStatus(taskId, request));
    }

    @DeleteMapping("/{taskId}")
    ResponseEntity<Void> delete(@PathVariable String taskId) {
        service.delete(taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/recommend-type")
    ApiResponse<?> recommendType(@PathVariable String taskId) {
        return ApiResponse.success(
                assistant.recommendDocumentTypes(service.require(taskId).getWritingPurpose()));
    }
}
