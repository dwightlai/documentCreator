package cn.gov.document.platform.review.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.review.application.ReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/writing-tasks/{writingTaskId}")
public class WritingReviewController {
    private final ReviewService service;

    public WritingReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping("/initiate-review")
    ApiResponse<ReviewDtos.TaskResponse> initiate(
            @PathVariable String writingTaskId, @RequestBody(required = false) InitiateRequest request) {
        String planId = request == null ? null : request.reviewPlanId();
        return ApiResponse.success(service.create(new ReviewDtos.CreateRequest(writingTaskId, planId, null)));
    }

    /** 可选指定审校方案；未指定时使用默认“公文规范审校”。 */
    record InitiateRequest(String reviewPlanId) {}
}
