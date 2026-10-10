package cn.gov.document.platform.review.api;

import cn.gov.document.platform.review.domain.ReviewIssueEntity;
import cn.gov.document.platform.review.domain.ReviewTaskEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record CreateRequest(@NotBlank String writingTaskId, String reviewPlanId, String callbackUrl) {}

    public record ProcessRequest(@Size(max = 1000) String note) {}

    public record BatchRequest(
            @NotEmpty @Size(max = 500) List<@NotBlank String> issueNumbers, @Size(max = 1000) String note) {}

    public record AddToDictionaryRequest(
            @NotBlank String dictionaryId,
            @Size(max = 500) String sourceReference,
            @Size(max = 1000) String usageNote) {}

    public record TaskResponse(
            String id,
            String sourceSystem,
            String businessId,
            String documentFileId,
            String documentFileHash,
            String outputTemplateId,
            String callbackUrl,
            String writingTaskId,
            String documentId,
            String documentTypeCode,
            String reviewPlanId,
            String status,
            String initiatorId,
            long totalIssues,
            long pendingIssues,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        public static TaskResponse from(ReviewTaskEntity task, List<ReviewIssueEntity> issues) {
            return new TaskResponse(
                    task.getId(),
                    task.getSourceSystem(),
                    task.getBusinessId(),
                    task.getDocumentFileId(),
                    task.getDocumentFileHash(),
                    task.getOutputTemplateId(),
                    task.getCallbackUrl(),
                    task.getWritingTaskId(),
                    task.getDocumentId(),
                    task.getDocumentTypeCode(),
                    task.getReviewPlanId(),
                    task.getStatus(),
                    task.getInitiatorId(),
                    issues.size(),
                    issues.stream()
                            .filter(i -> "待处理".equals(i.getProcessingStatus()))
                            .count(),
                    task.getCreatedAt(),
                    task.getUpdatedAt());
        }
    }

    public record IssueResponse(
            String id,
            String issueNumber,
            String issueCategory,
            String riskLevel,
            Integer pageNumber,
            Integer paragraphNumber,
            String paragraphId,
            Integer charPositionStart,
            Integer charPositionEnd,
            String originalText,
            String suggestedFix,
            String issueExplanation,
            String ruleBasis,
            String detectionMethod,
            BigDecimal confidenceScore,
            String processingStatus,
            String processingNote,
            Integer lockVersion) {
        public static IssueResponse from(ReviewIssueEntity i) {
            return new IssueResponse(
                    i.getId(),
                    i.getIssueNumber(),
                    i.getIssueCategory(),
                    i.getRiskLevel(),
                    i.getPageNumber(),
                    i.getParagraphNumber(),
                    i.getParagraphId(),
                    i.getCharPositionStart(),
                    i.getCharPositionEnd(),
                    i.getOriginalText(),
                    i.getSuggestedFix(),
                    i.getIssueExplanation(),
                    i.getRuleBasis(),
                    i.getDetectionMethod(),
                    i.getConfidenceScore(),
                    i.getProcessingStatus(),
                    i.getProcessingNote(),
                    i.getLockVersion());
        }
    }

    public record Statistics(
            long total, Map<String, Long> byRisk, Map<String, Long> byCategory, Map<String, Long> byStatus) {}
}
