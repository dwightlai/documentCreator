package cn.gov.document.platform.knowledge.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.job.api.AsyncJobDtos;
import cn.gov.document.platform.job.application.AsyncJobService;
import cn.gov.document.platform.knowledge.application.KnowledgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;
    private final AsyncJobService jobService;
    private final ObjectMapper objectMapper;

    public KnowledgeController(KnowledgeService service, AsyncJobService jobService, ObjectMapper objectMapper) {
        this.service = service;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/sources")
    ApiResponse<List<KnowledgeDtos.SourceResponse>> sources() {
        return ApiResponse.success(service.sources());
    }

    @PostMapping("/sources")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.SourceResponse> create(@Valid @RequestBody KnowledgeDtos.CreateSourceRequest request) {
        return ApiResponse.success(service.createSource(request));
    }

    @GetMapping("/sources/{sourceId}/documents")
    ApiResponse<List<KnowledgeDtos.DocumentResponse>> documents(@PathVariable String sourceId) {
        return ApiResponse.success(service.documents(sourceId));
    }

    @PostMapping(value = "/sources/{sourceId}/documents/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.DocumentResponse> upload(
            @PathVariable String sourceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String metadata,
            @RequestParam(required = false) String authorityLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) String permissionLevel) {
        return ApiResponse.success(service.upload(
                sourceId, file, title, metadata, authorityLevel, effectiveDate, expiryDate, permissionLevel));
    }

    @PostMapping("/sources/{sourceId}/documents/{documentId}/parse")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.ParseResponse> parse(@PathVariable String sourceId, @PathVariable String documentId) {
        return ApiResponse.success(service.parse(sourceId, documentId));
    }

    @PostMapping("/sources/{sourceId}/documents/{documentId}/parse-async")
    @PreAuthorize("hasRole('platform.admin')")
    ResponseEntity<ApiResponse<AsyncJobDtos.Response>> parseAsync(
            @PathVariable String sourceId, @PathVariable String documentId) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("sourceId", sourceId, "documentId", documentId));
        AsyncJobDtos.Response job =
                jobService.submit(new AsyncJobDtos.SubmitRequest("KNOWLEDGE_PARSE", documentId, payload, 5));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/" + job.id()))
                .body(ApiResponse.success(job));
    }

    @PostMapping("/sources/{sourceId}/documents/{documentId}/vectorize")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<Void> vectorize(@PathVariable String sourceId, @PathVariable String documentId) {
        throw cn.gov.document.platform.common.error.BusinessException.unprocessable("向量服务尚未配置；当前仅启用权限前置的全文检索降级");
    }

    @GetMapping("/search")
    ApiResponse<List<KnowledgeDtos.SearchResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(service.search(q, sourceId, limit));
    }
}
