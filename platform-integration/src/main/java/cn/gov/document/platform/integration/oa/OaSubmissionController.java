package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.common.api.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OaSubmissionController {
    private final OaWritingService service;

    public OaSubmissionController(OaWritingService service) {
        this.service = service;
    }

    /** 经提交门禁检查后，将当前文稿版本提交回 OA。 */
    @PostMapping("/api/v1/writing-tasks/{writingTaskId}/submit-to-oa")
    ApiResponse<OaIntegrationDtos.SubmitResponse> submit(
            @PathVariable String writingTaskId,
            @Valid @RequestBody(required = false) OaIntegrationDtos.SubmitRequest request)
            throws JsonProcessingException {
        return ApiResponse.success(service.submit(writingTaskId, request));
    }
}
