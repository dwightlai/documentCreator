package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.DocumentSuggestionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/document/suggestions")
public class DocumentSuggestionController {
    private final DocumentSuggestionService service;

    public DocumentSuggestionController(DocumentSuggestionService service) {
        this.service = service;
    }

    @PostMapping
    ApiResponse<DocumentSuggestionDtos.SuggestionResponse> create(
            @PathVariable String taskId, @Valid @RequestBody DocumentSuggestionDtos.CreateRequest request) {
        return ApiResponse.success(service.create(taskId, request));
    }

    @GetMapping
    ApiResponse<List<DocumentSuggestionDtos.SuggestionResponse>> list(
            @PathVariable String taskId, @RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(taskId, status));
    }

    @PostMapping("/{suggestionId}/accept")
    ApiResponse<DocumentDtos.DocumentResponse> accept(
            @PathVariable String taskId,
            @PathVariable String suggestionId,
            @Valid @RequestBody(required = false) DocumentSuggestionDtos.DecisionRequest request) {
        return ApiResponse.success(service.accept(taskId, suggestionId, request));
    }

    @PostMapping("/{suggestionId}/reject")
    ApiResponse<DocumentSuggestionDtos.SuggestionResponse> reject(
            @PathVariable String taskId,
            @PathVariable String suggestionId,
            @Valid @RequestBody(required = false) DocumentSuggestionDtos.DecisionRequest request) {
        return ApiResponse.success(service.reject(taskId, suggestionId, request));
    }
}
