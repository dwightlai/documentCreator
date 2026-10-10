package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.WritingOutlineEntity;
import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class WritingOutlineDtos {
    private WritingOutlineDtos() {}

    public record NodeDraft(String title, String purpose, int targetWordCount, boolean required) {}

    public record NodeRequest(
            String parentNodeId,
            @NotNull @Min(1) Integer order,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 10000) String purpose,
            @Size(max = 10000) String keyFacts,
            List<String> referencedMaterialIds,
            @Min(1) Integer targetWordCount,
            Boolean required,
            @Size(max = 5000) String humanNote,
            Integer lockVersion) {}

    public record NodeResponse(
            String id,
            String parentNodeId,
            Integer order,
            String title,
            String purpose,
            String keyFacts,
            String referencedMaterialIds,
            Integer targetWordCount,
            Boolean required,
            String generationStatus,
            String humanNote,
            Integer lockVersion) {
        static NodeResponse from(WritingOutlineNodeEntity entity) {
            return new NodeResponse(
                    entity.getId(),
                    entity.getParentNodeId(),
                    entity.getNodeOrder(),
                    entity.getNodeTitle(),
                    entity.getWritingPurpose(),
                    entity.getKeyFacts(),
                    entity.getReferencedMaterialIds(),
                    entity.getTargetWordCount(),
                    entity.getIsRequired(),
                    entity.getGenerationStatus(),
                    entity.getHumanNote(),
                    entity.getLockVersion());
        }
    }

    public record Response(
            String id,
            String taskId,
            Integer version,
            Boolean confirmed,
            String confirmedBy,
            OffsetDateTime confirmedAt,
            String structureCheckResult,
            Integer lockVersion,
            List<NodeResponse> nodes) {
        public static Response from(WritingOutlineEntity outline, List<WritingOutlineNodeEntity> nodes) {
            return new Response(
                    outline.getId(),
                    outline.getTaskId(),
                    outline.getOutlineVersion(),
                    outline.getIsConfirmed(),
                    outline.getConfirmedBy(),
                    outline.getConfirmedAt(),
                    outline.getStructureCheckResult(),
                    outline.getLockVersion(),
                    nodes.stream().map(NodeResponse::from).toList());
        }
    }
}
