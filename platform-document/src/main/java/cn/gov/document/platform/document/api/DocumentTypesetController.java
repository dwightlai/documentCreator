package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.DocumentTypesetService;
import cn.gov.document.platform.document.domain.DocumentTypesetEntity;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/document/typesets")
public class DocumentTypesetController {
    private final DocumentTypesetService service;

    public DocumentTypesetController(DocumentTypesetService service) {
        this.service = service;
    }

    @PostMapping
    ApiResponse<DocumentTypesetDtos.TypesetResponse> create(
            @PathVariable String taskId, @Valid @RequestBody DocumentTypesetDtos.CreateRequest request) {
        return ApiResponse.success(service.create(taskId, request));
    }

    @GetMapping("/latest")
    ApiResponse<DocumentTypesetDtos.TypesetResponse> latest(@PathVariable String taskId) {
        return ApiResponse.success(service.latest(taskId));
    }

    @GetMapping("/{typesetId}/file")
    ResponseEntity<byte[]> file(@PathVariable String taskId, @PathVariable String typesetId) {
        DocumentTypesetEntity typeset = service.require(taskId, typesetId);
        byte[] body = service.file(typeset);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("定稿稿-V" + typeset.getDocumentVersion() + ".docx", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
