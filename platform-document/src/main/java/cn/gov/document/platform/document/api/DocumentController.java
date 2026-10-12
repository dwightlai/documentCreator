package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.document.application.DocxExporter;
import cn.gov.document.platform.document.application.HtmlExporter;
import cn.gov.document.platform.document.application.PdfExporter;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/document")
public class DocumentController {
    private final DocumentService service;
    private final DocxExporter exporter;
    private final HtmlExporter htmlExporter;
    private final PdfExporter pdfExporter;

    public DocumentController(
            DocumentService service, DocxExporter exporter, HtmlExporter htmlExporter, PdfExporter pdfExporter) {
        this.service = service;
        this.exporter = exporter;
        this.htmlExporter = htmlExporter;
        this.pdfExporter = pdfExporter;
    }

    @GetMapping
    ApiResponse<DocumentDtos.DocumentResponse> get(@PathVariable String taskId) {
        return ApiResponse.success(service.get(taskId));
    }

    @GetMapping("/versions")
    ApiResponse<List<DocumentDtos.VersionResponse>> versions(@PathVariable String taskId) {
        return ApiResponse.success(service.versions(taskId));
    }

    @GetMapping("/versions/{versionNumber}")
    ApiResponse<DocumentDtos.DocumentResponse> version(@PathVariable String taskId, @PathVariable int versionNumber) {
        return ApiResponse.success(service.version(taskId, versionNumber));
    }

    @PostMapping("/versions/{versionNumber}/restore")
    ApiResponse<DocumentDtos.DocumentResponse> restore(@PathVariable String taskId, @PathVariable int versionNumber) {
        return ApiResponse.success(service.restore(taskId, versionNumber));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('platform.admin')")
    @PutMapping("/versions/{versionNumber}/protection")
    ApiResponse<DocumentDtos.VersionResponse> protectVersion(
            @PathVariable String taskId,
            @PathVariable int versionNumber,
            @RequestBody DocumentDtos.VersionProtectionRequest request) {
        return ApiResponse.success(service.protectVersion(taskId, versionNumber, request.protectedVersion()));
    }

    @PutMapping("/paragraphs/{paragraphId}")
    ApiResponse<DocumentDtos.DocumentResponse> updateParagraph(
            @PathVariable String taskId,
            @PathVariable String paragraphId,
            @Valid @RequestBody DocumentDtos.UpdateParagraphRequest request) {
        return ApiResponse.success(service.updateParagraph(taskId, paragraphId, request));
    }

    @PostMapping("/paragraphs/{paragraphId}/lock")
    ApiResponse<DocumentDtos.DocumentResponse> lock(@PathVariable String taskId, @PathVariable String paragraphId) {
        return ApiResponse.success(service.setLocked(taskId, paragraphId, true));
    }

    @DeleteMapping("/paragraphs/{paragraphId}/lock")
    ApiResponse<DocumentDtos.DocumentResponse> unlock(@PathVariable String taskId, @PathVariable String paragraphId) {
        return ApiResponse.success(service.setLocked(taskId, paragraphId, false));
    }

    /** 获取或续期段落编辑租约（默认 5 分钟），编辑期间需定期调用以续期。 */
    @PostMapping("/paragraphs/{paragraphId}/edit-lease")
    ApiResponse<DocumentDtos.EditLeaseResponse> acquireEditLease(
            @PathVariable String taskId, @PathVariable String paragraphId) {
        return ApiResponse.success(service.acquireEditLease(taskId, paragraphId));
    }

    @DeleteMapping("/paragraphs/{paragraphId}/edit-lease")
    ApiResponse<Void> releaseEditLease(@PathVariable String taskId, @PathVariable String paragraphId) {
        service.releaseEditLease(taskId, paragraphId);
        return ApiResponse.success(null);
    }

    /** §33 AI 生成内容标识摘要：AI 参与内容占比、模型版本、生成记录数。 */
    @GetMapping("/ai-disclosure")
    ApiResponse<DocumentService.AiDisclosure> aiDisclosure(@PathVariable String taskId) {
        return ApiResponse.success(service.aiDisclosure(taskId));
    }

    @PostMapping("/export")
    ResponseEntity<byte[]> export(
            @PathVariable String taskId, @RequestBody(required = false) DocumentDtos.ExportRequest request) {
        String format = request == null || request.format() == null
                ? "DOCX"
                : request.format().toUpperCase();
        boolean retainCitations = request == null || request.retainCitations() == null || request.retainCitations();
        boolean sourceComments = request == null || request.sourceComments() == null || request.sourceComments();
        boolean appendDisclosure = request != null && Boolean.TRUE.equals(request.aiDisclosure());
        DocumentService.ExportData exported = service.exportData(taskId, retainCitations);
        DocumentService.AiDisclosure disclosure = service.aiDisclosure(taskId);
        DocumentService.ExportData data = appendDisclosure
                ? new DocumentService.ExportData(
                        exported.document(), DisclosureParagraph.append(exported.paragraphs(), disclosure))
                : exported;
        byte[] body;
        MediaType contentType;
        String extension;
        switch (format) {
            case "DOCX" -> {
                body = exporter.export(data.document(), data.paragraphs(), disclosure, sourceComments);
                contentType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                extension = "docx";
            }
            case "HTML" -> {
                body = htmlExporter.export(data.document(), data.paragraphs());
                contentType = MediaType.TEXT_HTML;
                extension = "html";
            }
            case "PDF" -> {
                body = pdfExporter.export(data.document(), data.paragraphs());
                contentType = MediaType.APPLICATION_PDF;
                extension = "pdf";
            }
            default -> throw BusinessException.unprocessable("导出格式仅支持 DOCX、HTML 或 PDF");
        }
        String filename = data.document().getTitle().replaceAll("[\\/:*?\"<>|]", "_") + "." + extension;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
