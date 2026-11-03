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

    @PutMapping("/sources/{sourceId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.SourceResponse> updateSource(
            @PathVariable String sourceId, @Valid @RequestBody KnowledgeDtos.UpdateSourceRequest request) {
        return ApiResponse.success(service.updateSource(sourceId, request));
    }

    @PostMapping("/sources/{sourceId}/reclassify")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.SourceResponse> reclassifySource(
            @PathVariable String sourceId, @Valid @RequestBody KnowledgeDtos.ReclassifySourceRequest request) {
        return ApiResponse.success(service.reclassifySource(sourceId, request.category(), request.lockVersion()));
    }

    @DeleteMapping("/sources/{sourceId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<Void> deleteSource(@PathVariable String sourceId) {
        service.deleteSource(sourceId);
        return ApiResponse.success(null);
    }

    @GetMapping("/sources/{sourceId}/documents")
    ApiResponse<List<KnowledgeDtos.DocumentResponse>> documents(@PathVariable String sourceId) {
        return ApiResponse.success(service.documents(sourceId));
    }

    @PutMapping("/sources/{sourceId}/documents/{documentId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.DocumentResponse> updateDocument(
            @PathVariable String sourceId, @PathVariable String documentId,
            @Valid @RequestBody KnowledgeDtos.UpdateDocumentRequest request) {
        return ApiResponse.success(service.updateDocument(sourceId, documentId, request));
    }

    @DeleteMapping("/sources/{sourceId}/documents/{documentId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<Void> deleteDocument(@PathVariable String sourceId, @PathVariable String documentId) {
        service.deleteDocument(sourceId, documentId);
        return ApiResponse.success(null);
    }

    @GetMapping("/sources/{sourceId}/documents/{documentId}/chunks")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<List<KnowledgeDtos.ChunkResponse>> chunks(@PathVariable String sourceId, @PathVariable String documentId) {
        return ApiResponse.success(service.chunks(sourceId, documentId));
    }

    @GetMapping("/sources/{sourceId}/documents/{documentId}/read")
    @PreAuthorize("hasAnyRole('platform.admin','platform.writer')")
    ApiResponse<KnowledgeDtos.ReadResponse> read(@PathVariable String sourceId, @PathVariable String documentId) {
        return ApiResponse.success(service.readDocument(sourceId, documentId));
    }

    @GetMapping("/sources/{sourceId}/documents/{documentId}/revisions")
    @PreAuthorize("hasAnyRole('platform.admin','platform.writer')")
    ApiResponse<List<KnowledgeDtos.RevisionResponse>> revisions(@PathVariable String sourceId, @PathVariable String documentId) {
        return ApiResponse.success(service.revisions(sourceId, documentId));
    }

    @PutMapping("/sources/{sourceId}/documents/{documentId}/chunks/{chunkId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<KnowledgeDtos.ChunkResponse> updateChunk(
            @PathVariable String sourceId, @PathVariable String documentId, @PathVariable String chunkId,
            @Valid @RequestBody KnowledgeDtos.UpdateChunkRequest request) {
        return ApiResponse.success(service.updateChunk(sourceId, documentId, chunkId, request));
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
    ApiResponse<KnowledgeDtos.ParseResponse> vectorize(@PathVariable String sourceId, @PathVariable String documentId) {
        return ApiResponse.success(service.vectorize(sourceId, documentId));
    }

    @GetMapping("/search")
    ApiResponse<List<KnowledgeDtos.SearchResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String mode) {
        return ApiResponse.success(service.search(q, sourceId, limit, mode));
    }
}
