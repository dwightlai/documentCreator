package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.DocumentTypeConfigEntity;
import cn.gov.document.platform.writing.domain.DocumentTypeConfigVersionEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentTypeConfigDtos {
    private DocumentTypeConfigDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 20) String typeCode,
            @NotBlank @Size(max = 50) String typeName,
            @NotBlank @Pattern(regexp = "法定公文|事务文书") String documentCategory,
            @Size(max = 5000) String applicableScenario,
            @Pattern(regexp = "上行|下行|平行|内部") String direction,
            @NotEmpty List<@NotBlank String> requiredFields,
            @NotEmpty List<@NotBlank String> recommendedStructure,
            List<@NotBlank String> forbiddenPatterns,
            List<@NotBlank String> closingPatterns,
            @Size(max = 1000) String changeDescription) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 50) String typeName,
            @NotBlank @Pattern(regexp = "法定公文|事务文书") String documentCategory,
            @Size(max = 5000) String applicableScenario,
            @Pattern(regexp = "上行|下行|平行|内部") String direction,
            @NotEmpty List<@NotBlank String> requiredFields,
            @NotEmpty List<@NotBlank String> recommendedStructure,
            List<@NotBlank String> forbiddenPatterns,
            List<@NotBlank String> closingPatterns,
            @NotBlank @Size(max = 1000) String changeDescription,
            @NotNull Integer lockVersion) {}

    public record TestResponse(boolean passed, List<String> errors, List<String> warnings) {}

    public record Response(
            String id,
            String typeCode,
            String typeName,
            String documentCategory,
            String applicableScenario,
            String direction,
            List<String> requiredFields,
            List<String> recommendedStructure,
            List<String> forbiddenPatterns,
            List<String> closingPatterns,
            int currentVersion,
            String lifecycleStatus,
            int lockVersion,
            OffsetDateTime updatedAt) {
        public static Response from(
                DocumentTypeConfigEntity e,
                List<String> required,
                List<String> structure,
                List<String> forbidden,
                List<String> closing) {
            return new Response(
                    e.getId(),
                    e.getTypeCode(),
                    e.getTypeName(),
                    e.getDocumentCategory(),
                    e.getApplicableScenario(),
                    e.getDirection(),
                    required,
                    structure,
                    forbidden,
                    closing,
                    e.getCurrentVersion(),
                    e.getLifecycleStatus(),
                    e.getLockVersion(),
                    e.getUpdatedAt());
        }
    }

    public record VersionResponse(
            int versionNumber,
            String fullConfig,
            String changeDescription,
            String testResults,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime createdAt) {
        public static VersionResponse from(DocumentTypeConfigVersionEntity e) {
            return new VersionResponse(
                    e.getVersionNumber(),
                    e.getFullConfig(),
                    e.getChangeDescription(),
                    e.getTestResults(),
                    e.getApprovedBy(),
                    e.getApprovedAt(),
                    e.getCreatedAt());
        }
    }
}
