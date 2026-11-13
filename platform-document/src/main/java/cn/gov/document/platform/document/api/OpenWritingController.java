package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.DocumentAssistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open/writing")
public class OpenWritingController {
    private final DocumentAssistService assistService;

    public OpenWritingController(DocumentAssistService assistService) {
        this.assistService = assistService;
    }

    @PostMapping("/summary")
    ApiResponse<DocumentDtos.SummaryResponse> summary(@Valid @RequestBody DocumentDtos.OpenWritingRequest request) {
        return ApiResponse.success(assistService.openSummary(request));
    }

    @PostMapping("/polish")
    ApiResponse<DocumentDtos.AssistPreviewResponse> polish(
            @Valid @RequestBody DocumentDtos.OpenWritingRequest request) {
        return ApiResponse.success(assistService.openPreview("润色", request));
    }

    @PostMapping("/expand")
    ApiResponse<DocumentDtos.AssistPreviewResponse> expand(
            @Valid @RequestBody DocumentDtos.OpenWritingRequest request) {
        return ApiResponse.success(assistService.openPreview("扩写", request));
    }
}
