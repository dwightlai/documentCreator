package cn.gov.document.platform.review.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.review.application.SubmissionGateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubmissionGateController {
    private final SubmissionGateService service;

    public SubmissionGateController(SubmissionGateService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/submission-gate")
    ApiResponse<SubmissionGateDtos.Config> config() {
        return ApiResponse.success(service.config());
    }

    @PutMapping("/api/v1/submission-gate")
    ApiResponse<SubmissionGateDtos.Config> update(@RequestBody SubmissionGateDtos.Config request) {
        return ApiResponse.success(service.updateConfig(request));
    }

    /** 提交前自检：返回每条门禁规则的结论，供前端展示。 */
    @GetMapping("/api/v1/writing-tasks/{writingTaskId}/submission-check")
    ApiResponse<SubmissionGateDtos.CheckResult> check(@PathVariable String writingTaskId) {
        return ApiResponse.success(service.check(writingTaskId));
    }
}
