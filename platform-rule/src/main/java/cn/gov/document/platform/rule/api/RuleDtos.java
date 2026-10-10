package cn.gov.document.platform.rule.api;

import cn.gov.document.platform.rule.domain.ReviewRuleEntity;
import cn.gov.document.platform.rule.domain.RuleVersionEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class RuleDtos {
    private RuleDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 50) String ruleCode,
            @NotBlank @Size(max = 200) String ruleName,
            @NotBlank String category,
            List<String> applicableDocumentTypes,
            @NotBlank String riskLevel,
            @NotBlank String detectionMethod,
            @NotBlank @Size(max = 20000) String ruleExpression,
            String basis,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String changeDescription) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String ruleName,
            @NotBlank String category,
            List<String> applicableDocumentTypes,
            @NotBlank String riskLevel,
            @NotBlank String detectionMethod,
            @NotBlank @Size(max = 20000) String ruleExpression,
            String basis,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            @NotBlank String changeDescription,
            @NotNull Integer lockVersion) {}

    public record TestCase(@NotBlank @Size(max = 30000) String text, @NotNull Integer expectedMatchCount) {}

    public record TestRequest(@NotEmpty @Size(max = 100) List<@Valid TestCase> cases) {}

    public record CaseResult(
            int index, int actualMatchCount, int expectedMatchCount, boolean passed, List<String> matches) {}

    public record TestResponse(boolean passed, int caseCount, int passedCount, List<CaseResult> results) {}

    public record Response(
            String id,
            String ruleCode,
            String ruleName,
            String category,
            List<String> applicableDocumentTypes,
            String riskLevel,
            String detectionMethod,
            String ruleExpression,
            String basis,
            String lifecycleStatus,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            int currentVersion,
            boolean enabled,
            int lockVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        public static Response from(ReviewRuleEntity e, List<String> types) {
            return new Response(
                    e.getId(),
                    e.getRuleCode(),
                    e.getRuleName(),
                    e.getCategory(),
                    types,
                    e.getRiskLevel(),
                    e.getDetectionMethod(),
                    e.getRuleExpression(),
                    e.getBasis(),
                    e.getLifecycleStatus(),
                    e.getEffectiveDate(),
                    e.getExpiryDate(),
                    e.getCurrentVersion(),
                    Boolean.TRUE.equals(e.getEnabled()),
                    e.getLockVersion(),
                    e.getCreatedAt(),
                    e.getUpdatedAt());
        }
    }

    public record VersionResponse(
            int versionNumber,
            String ruleExpression,
            String changeDescription,
            String approvedBy,
            OffsetDateTime approvedAt,
            String testResults,
            OffsetDateTime createdAt) {
        public static VersionResponse from(RuleVersionEntity e) {
            return new VersionResponse(
                    e.getVersionNumber(),
                    e.getRuleExpression(),
                    e.getChangeDescription(),
                    e.getApprovedBy(),
                    e.getApprovedAt(),
                    e.getTestResults(),
                    e.getCreatedAt());
        }
    }
}
