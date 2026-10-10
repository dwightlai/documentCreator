package cn.gov.document.platform.model.api;

import cn.gov.document.platform.model.domain.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class PromptTemplateDtos {
    private PromptTemplateDtos() {}

    public record Request(
            @NotBlank @Size(max = 50) String templateCode,
            @NotBlank @Size(max = 200) String templateName,
            @NotBlank @Size(max = 50) String applicableScene,
            @Size(max = 20) String documentTypeCode,
            List<@NotBlank String> applicableModels,
            @NotBlank @Size(max = 20000) String systemRole,
            @Size(max = 20000) String documentTypeMethod,
            Map<String, Object> inputFields,
            @Size(max = 20000) String materialCitationRule,
            @NotBlank @Size(max = 20000) String antiFabricationRule,
            @NotBlank @Size(max = 20000) String outputFormat,
            @Size(max = 20000) String riskConstraint,
            Map<String, Object> modelParameters,
            List<@NotBlank String> testSampleIds,
            @Size(max = 1000) String changeDescription,
            Integer lockVersion) {}

    public record TestRequest(@NotNull Map<String, Object> variables) {}

    public record TestResponse(
            boolean passed, String renderedPrompt, List<String> unresolvedPlaceholders, String promptHash) {}

    public record Response(
            String id,
            String templateCode,
            String templateName,
            String applicableScene,
            String documentTypeCode,
            List<String> applicableModels,
            String systemRole,
            String documentTypeMethod,
            Map<String, Object> inputFields,
            String materialCitationRule,
            String antiFabricationRule,
            String outputFormat,
            String riskConstraint,
            Map<String, Object> modelParameters,
            List<String> testSampleIds,
            int currentVersion,
            String lifecycleStatus,
            int lockVersion,
            OffsetDateTime updatedAt) {}

    public record VersionResponse(
            int versionNumber,
            String systemRole,
            String fullPrompt,
            String changeDescription,
            String testResults,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime createdAt) {
        public static VersionResponse from(PromptTemplateVersionEntity e) {
            return new VersionResponse(
                    e.getVersionNumber(),
                    e.getSystemRole(),
                    e.getFullPrompt(),
                    e.getChangeDescription(),
                    e.getTestResults(),
                    e.getApprovedBy(),
                    e.getApprovedAt(),
                    e.getCreatedAt());
        }
    }
}
