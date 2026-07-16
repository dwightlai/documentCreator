package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/output-templates")
public class OutputTemplateController {
    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final OutputTemplateService service;
    private final DocxFormatReviewService formatReviewService;

    public OutputTemplateController(OutputTemplateService service, DocxFormatReviewService formatReviewService) {
        this.service = service;
        this.formatReviewService = formatReviewService;
    }

    @GetMapping
    ApiResponse<List<OutputTemplateDtos.Response>> list(
            @RequestParam(required = false) String documentTypeCode,
            @RequestParam(required = false) String lifecycleStatus) {
        return ApiResponse.success(service.list(documentTypeCode, lifecycleStatus));
    }

    @GetMapping("/{templateId}")
    ApiResponse<OutputTemplateDtos.Response> get(@PathVariable String templateId) {
        return ApiResponse.success(service.get(templateId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<OutputTemplateDtos.Response> create(
            @RequestPart("metadata") @Valid OutputTemplateDtos.CreateMetadata metadata,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(service.create(metadata, file.getOriginalFilename(), file.getBytes()));
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<OutputTemplateDtos.Response> update(
            @PathVariable String templateId,
            @Valid @RequestBody OutputTemplateDtos.UpdateMetadata request) {
        return ApiResponse.success(service.update(templateId, request));
    }

    @PutMapping(value = "/{templateId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<OutputTemplateDtos.Response> replaceFile(
            @PathVariable String templateId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String changeNote) throws IOException {
        return ApiResponse.success(service.replaceFile(
                templateId, file.getOriginalFilename(), file.getBytes(), changeNote));
    }

    @PostMapping("/{templateId}/parse")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocxTemplateEngine.Inspection> parse(@PathVariable String templateId) {
        return ApiResponse.success(service.parse(templateId));
    }

    @PostMapping("/{templateId}/test")
    @PreAuthorize("hasRole('platform.admin')")
    ResponseEntity<byte[]> test(
            @PathVariable String templateId,
            @RequestBody OutputTemplateDtos.RenderRequest request) {
        return docx(service.test(templateId, request), "template-test.docx");
    }

    @PostMapping("/{templateId}/render")
    ResponseEntity<byte[]> render(
            @PathVariable String templateId,
            @RequestBody OutputTemplateDtos.RenderRequest request) {
        return docx(service.render(templateId, request), "rendered-document.docx");
    }

    @PostMapping("/{templateId}/publish")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<OutputTemplateDtos.Response> publish(@PathVariable String templateId) {
        return ApiResponse.success(service.publish(templateId));
    }

    @PostMapping(value = "/{templateId}/format-review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<DocxFormatReviewService.Result> formatReview(
            @PathVariable String templateId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(formatReviewService.compare(templateId, file.getBytes()));
    }

    @GetMapping("/{templateId}/versions")
    ApiResponse<List<OutputTemplateDtos.VersionResponse>> versions(@PathVariable String templateId) {
        return ApiResponse.success(service.versions(templateId));
    }

    @GetMapping("/by-document-type/{documentTypeCode}")
    ApiResponse<List<OutputTemplateDtos.Response>> byDocumentType(@PathVariable String documentTypeCode) {
        return ApiResponse.success(service.effectiveByDocumentType(documentTypeCode));
    }

    private ResponseEntity<byte[]> docx(byte[] body, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(DOCX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8).build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
