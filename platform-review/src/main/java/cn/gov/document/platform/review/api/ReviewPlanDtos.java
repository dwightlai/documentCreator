package cn.gov.document.platform.review.api;

import cn.gov.document.platform.review.domain.ReviewPlanEntity;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class ReviewPlanDtos {
    private ReviewPlanDtos() {}

    public record Request(
            @NotBlank @Size(max = 50) String planCode,
            @NotBlank @Size(max = 200) String planName,
            String applicableScenarios,
            @NotEmpty List<@Pattern(regexp = "L[1-7]") String> reviewLevels,
            String ruleSetVersion,
            List<String> knowledgeScope,
            boolean allowExternalModel,
            @NotBlank @Pattern(regexp = "提示|一般|重要|严重") String riskThreshold,
            boolean checkAttachments,
            boolean allowAutoFix,
            boolean generateReport,
            Integer lockVersion) {}

    public record Response(
            String id,
            String planCode,
            String planName,
            String applicableScenarios,
            List<String> reviewLevels,
            String ruleSetVersion,
            List<String> knowledgeScope,
            boolean allowExternalModel,
            String riskThreshold,
            boolean checkAttachments,
            boolean allowAutoFix,
            boolean generateReport,
            boolean custom,
            int lockVersion,
            OffsetDateTime updatedAt) {
        public static Response from(ReviewPlanEntity e, List<String> levels, List<String> scope) {
            return new Response(
                    e.getId(),
                    e.getPlanCode(),
                    e.getPlanName(),
                    e.getApplicableScenarios(),
                    levels,
                    e.getRuleSetVersion(),
                    scope,
                    Boolean.TRUE.equals(e.getAllowExternalModel()),
                    e.getRiskThreshold(),
                    Boolean.TRUE.equals(e.getCheckAttachments()),
                    Boolean.TRUE.equals(e.getAllowAutoFix()),
                    Boolean.TRUE.equals(e.getGenerateReport()),
                    Boolean.TRUE.equals(e.getIsCustom()),
                    e.getLockVersion(),
                    e.getUpdatedAt());
        }
    }
}
