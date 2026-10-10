package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.WritingIdeaEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class WritingPlanningDtos {
    private WritingPlanningDtos() {}

    public record UpdateIdeaRequest(
            @NotBlank @Size(max = 10000) String mainTheme,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 1000) String> coreViewpoints,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 1000) String> writingSequence,
            @Size(max = 20000) String mustExpressFacts,
            @Size(max = 20000) String optionalContent,
            @Size(max = 20000) String missingMaterials,
            @Size(max = 20000) String riskWarnings,
            boolean confirmed,
            @NotNull Integer lockVersion) {}

    public record IdeaResponse(
            String id,
            String taskId,
            int version,
            String mainTheme,
            List<String> coreViewpoints,
            List<String> writingSequence,
            String mustExpressFacts,
            String optionalContent,
            String missingMaterials,
            String riskWarnings,
            boolean confirmed,
            String confirmedBy,
            OffsetDateTime confirmedAt,
            int lockVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        public static IdeaResponse from(WritingIdeaEntity entity, List<String> viewpoints, List<String> sequence) {
            return new IdeaResponse(
                    entity.getId(),
                    entity.getTaskId(),
                    entity.getVersion(),
                    entity.getMainTheme(),
                    viewpoints,
                    sequence,
                    entity.getMustExpressFacts(),
                    entity.getOptionalContent(),
                    entity.getMissingMaterials(),
                    entity.getRiskWarnings(),
                    Boolean.TRUE.equals(entity.getIsConfirmed()),
                    entity.getConfirmedBy(),
                    entity.getConfirmedAt(),
                    entity.getLockVersion(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }
    }

    public record TitleCandidate(
            String titleText,
            String titleStructure,
            String applicableScenario,
            boolean includesIssuingAuthority,
            boolean includesSubject,
            boolean includesDocumentType,
            List<String> riskWarnings,
            double confidence) {}
}
