package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.DocumentGenerationStreamService;
import cn.gov.document.platform.document.application.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/generate")
public class DocumentGenerationController {
    private final DocumentService service;
    private final DocumentGenerationStreamService streamService;

    public DocumentGenerationController(DocumentService service, DocumentGenerationStreamService streamService) {
        this.service = service;
        this.streamService = streamService;
    }

    @PostMapping("/section")
    ApiResponse<DocumentDtos.DocumentResponse> generateSection(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.GenerateSectionRequest request) {
        return ApiResponse.success(service.generateSection(taskId, request));
    }

    @PostMapping(value = "/section/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter generateSectionStream(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.GenerateSectionRequest request) {
        return streamService.generate(taskId, request);
    }

    @PostMapping("/paragraph")
    ApiResponse<DocumentDtos.DocumentResponse> generateParagraph(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.GenerateSectionRequest request) {
        return ApiResponse.success(service.generateSection(taskId, request));
    }

    @PostMapping("/continue")
    ApiResponse<DocumentDtos.DocumentResponse> continueWriting(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.TransformRequest request) {
        return ApiResponse.success(service.transform(taskId, "续写", request));
    }

    @PostMapping("/rewrite")
    ApiResponse<DocumentDtos.DocumentResponse> rewrite(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.TransformRequest request) {
        return ApiResponse.success(service.transform(taskId, "改写", request));
    }

    @PostMapping("/compress")
    ApiResponse<DocumentDtos.DocumentResponse> compress(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.TransformRequest request) {
        return ApiResponse.success(service.transform(taskId, "压缩", request));
    }

    @PostMapping("/expand")
    ApiResponse<DocumentDtos.DocumentResponse> expand(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.TransformRequest request) {
        return ApiResponse.success(service.transform(taskId, "扩写", request));
    }

    @PostMapping("/selection-candidates")
    ApiResponse<DocumentDtos.SelectionCandidatesResponse> selectionCandidates(
            @PathVariable String taskId, @Valid @RequestBody DocumentDtos.SelectionCandidatesRequest request) {
        return ApiResponse.success(service.selectionCandidates(taskId, request));
    }
}
