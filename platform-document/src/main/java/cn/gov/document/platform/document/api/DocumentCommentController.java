package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.DocumentCommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents/{documentId}")
public class DocumentCommentController {
    private final DocumentCommentService service;

    public DocumentCommentController(DocumentCommentService service) {
        this.service = service;
    }

    @PostMapping("/comments")
    ApiResponse<DocumentCommentDtos.CommentResponse> create(
            @PathVariable String documentId, @Valid @RequestBody DocumentCommentDtos.CreateRequest request) {
        return ApiResponse.success(service.create(documentId, request));
    }

    @GetMapping("/comments")
    ApiResponse<List<DocumentCommentDtos.CommentResponse>> list(
            @PathVariable String documentId, @RequestParam(required = false) Boolean resolved) {
        return ApiResponse.success(service.list(documentId, resolved));
    }

    @PutMapping("/comments/{commentId}/resolve")
    ApiResponse<DocumentCommentDtos.CommentResponse> resolve(
            @PathVariable String documentId, @PathVariable String commentId) {
        return ApiResponse.success(service.resolve(documentId, commentId));
    }

    @GetMapping("/compare")
    ApiResponse<DocumentCommentDtos.CompareResponse> compare(
            @PathVariable String documentId, @RequestParam int fromVersion, @RequestParam int toVersion) {
        return ApiResponse.success(service.compare(documentId, fromVersion, toVersion));
    }
}
