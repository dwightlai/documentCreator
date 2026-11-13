package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.review.api.ReviewDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OaIntegrationDtos {
    private OaIntegrationDtos() {}

    public record CreateReviewRequest(
            @NotBlank @Size(max = 100) String businessId,
            @NotBlank @Size(max = 32) String writingTaskId,
            @Size(max = 100) String documentFileId,
            @Size(max = 500) String callbackUrl) {}

    /** §31.1 OA 发起写作传入的业务上下文；材料以文本片段传入，不接受外部文件地址以避免 SSRF。 */
    public record CreateWritingRequest(
            @NotBlank @Size(max = 100) String businessId,
            @NotBlank @Size(max = 20) String documentType,
            @Size(max = 20) String documentCategory,
            @NotBlank @Size(max = 200) String taskName,
            @NotBlank @Size(max = 500) String writingTopic,
            @Size(max = 2000) String writingPurpose,
            @Size(max = 100) String draftingDepartment,
            @Size(max = 200) String primaryRecipient,
            @Size(max = 20) String securityLevel,
            @Size(max = 20) java.util.List<@jakarta.validation.Valid Material> materials,
            @Size(max = 500) String callbackUrl) {}

    /** 第三方 OA 直接提交的正文。文种按标题识别。平台不保存文稿，也不按业务单据编号查找本地任务。 */
    public record SubmittedDocument(@NotBlank @Size(max = 20000) String text) {}

    /** 第三方 OA 直接提交待审正文。文种按标题识别，审校固定为内容安全审校。 */
    public record SubmittedReview(@NotBlank @Size(max = 20000) String text) {}

    public record Material(
            @NotBlank @Size(max = 500) String materialName,
            @NotBlank @Size(max = 20000) String content,
            @Size(max = 20) String authorityLevel) {}

    public record CreateWritingResponse(
            cn.gov.document.platform.writing.api.WritingTaskDtos.Response writingTask,
            int materialCount,
            String statusUrl) {}

    public record SubmitRequest(@Size(max = 500) String callbackUrl) {}

    public record SubmitResponse(
            String submissionId,
            String writingTaskId,
            int documentVersion,
            String businessId,
            String callbackJobId,
            cn.gov.document.platform.review.api.SubmissionGateDtos.CheckResult gate) {}

    public record SubmissionProgress(int documentVersion, java.time.OffsetDateTime submittedAt) {}

    public record CreateReviewResponse(ReviewDtos.TaskResponse reviewTask, String callbackJobId, String statusUrl) {}

    public record ReviewResult(
            ReviewDtos.TaskResponse reviewTask,
            java.util.List<ReviewDtos.IssueResponse> issues,
            java.util.List<cn.gov.document.platform.review.api.ReviewReportDtos.Response> reports) {}

    public record WritingStatus(
            cn.gov.document.platform.writing.api.WritingTaskDtos.Response writingTask,
            SubmissionProgress latestSubmission) {}
}
