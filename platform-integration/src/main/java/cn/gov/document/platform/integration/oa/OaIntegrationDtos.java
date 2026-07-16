package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.review.api.ReviewDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OaIntegrationDtos {
    private OaIntegrationDtos() { }

    public record CreateReviewRequest(
            @NotBlank @Size(max = 100) String businessId,
            @NotBlank @Size(max = 32) String writingTaskId,
            @Size(max = 100) String documentFileId,
            @Size(max = 32) String reviewPlanId,
            @Size(max = 500) String callbackUrl) { }

    public record CreateReviewResponse(
            ReviewDtos.TaskResponse reviewTask,
            String callbackJobId,
            String statusUrl) { }
}
