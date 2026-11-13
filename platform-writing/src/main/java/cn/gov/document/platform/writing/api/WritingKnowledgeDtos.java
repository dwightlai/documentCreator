package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.WritingKnowledgeSelectionEntity;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public final class WritingKnowledgeDtos {
    private WritingKnowledgeDtos() {}

    public record SelectRequest(
            @NotBlank String outlineNodeId,
            @NotBlank String sourceId,
            @NotBlank String documentId,
            @NotBlank String chunkId,
            @NotBlank String usageType,
            @NotBlank String selectionReason,
            boolean applicabilityConfirmed) {}

    public record SelectionResponse(
            String id,
            String taskId,
            String outlineNodeId,
            String sourceId,
            String documentId,
            String chunkId,
            String documentTitle,
            int documentVersion,
            String usageType,
            String completeness,
            String excerpt,
            String fileHash,
            String selectionReason,
            String selectedBy,
            OffsetDateTime selectedAt) {
        public static SelectionResponse from(WritingKnowledgeSelectionEntity e) {
            return new SelectionResponse(
                    e.getId(),
                    e.getTaskId(),
                    e.getOutlineNodeId(),
                    e.getSourceId(),
                    e.getKnowledgeDocumentId(),
                    e.getChunkId(),
                    e.getDocumentTitle(),
                    e.getDocumentVersion(),
                    e.getUsageType(),
                    e.getCompleteness(),
                    e.getExcerpt(),
                    e.getFileHash(),
                    e.getSelectionReason(),
                    e.getSelectedBy(),
                    e.getSelectedAt());
        }
    }
}
