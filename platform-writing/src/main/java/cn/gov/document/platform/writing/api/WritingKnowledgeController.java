package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.writing.application.WritingKnowledgeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/knowledge-selections")
@PreAuthorize("hasAnyRole('platform.admin','platform.writer')")
public class WritingKnowledgeController {
    private final WritingKnowledgeService service;

    public WritingKnowledgeController(WritingKnowledgeService service) { this.service = service; }

    @GetMapping
    ApiResponse<List<WritingKnowledgeDtos.SelectionResponse>> list(@PathVariable String taskId,
            @RequestParam(required = false) String outlineNodeId) {
        return ApiResponse.success(service.list(taskId, outlineNodeId));
    }

    @PostMapping
    ApiResponse<WritingKnowledgeDtos.SelectionResponse> select(@PathVariable String taskId,
            @Valid @RequestBody WritingKnowledgeDtos.SelectRequest request) {
        return ApiResponse.success(service.select(taskId, request));
    }

    @DeleteMapping("/{selectionId}")
    ApiResponse<Void> remove(@PathVariable String taskId, @PathVariable String selectionId) {
        service.remove(taskId, selectionId);
        return ApiResponse.success(null);
    }
}
