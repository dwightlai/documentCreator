package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.domain.DocumentVersionEntity;
import cn.gov.document.platform.document.domain.GenerationRecordEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record GenerateSectionRequest(@NotBlank String outlineNodeId, @Size(max = 1000) String instruction) {}

    public record TransformRequest(
            @NotBlank String paragraphId,
            @Size(max = 1000) String instruction,
            @Min(20) Integer targetWordCount,
            @NotNull Integer lockVersion) {}

    public record UpdateParagraphRequest(
            @NotBlank @Size(max = 30000) String plainText,
            @NotNull Integer lockVersion,
            @Size(max = 500) String changeDescription,
            Integer baseVersion) {
        public UpdateParagraphRequest(String plainText, Integer lockVersion, String changeDescription) {
            this(plainText, lockVersion, changeDescription, null);
        }
    }

    /** 段落编辑租约：持有期间其他用户不能保存该段落。 */
    public record EditLeaseResponse(String paragraphId, String holderId, java.time.Instant expiresAt) {}

    public record ExportRequest(String format, Boolean retainCitations) {}

    public record ParagraphResponse(
            String id,
            String outlineNodeId,
            int paragraphOrder,
            String contentSourceLevel,
            List<String> sourceMaterialIds,
            boolean locked,
            String plainText,
            int lockVersion) {
        static ParagraphResponse from(DocumentParagraphEntity entity) {
            return new ParagraphResponse(
                    entity.getId(),
                    entity.getOutlineNodeId(),
                    entity.getParagraphOrder(),
                    entity.getContentSourceLevel(),
                    parseIds(entity.getSourceMaterialIds()),
                    Boolean.TRUE.equals(entity.getIsLocked()),
                    entity.getPlainText(),
                    entity.getLockVersion());
        }
    }

    public record VersionResponse(
            int versionNumber,
            String versionType,
            String changeSource,
            String changeDescription,
            int wordCount,
            OffsetDateTime createdAt) {
        public static VersionResponse from(DocumentVersionEntity entity) {
            return new VersionResponse(
                    entity.getVersionNumber(),
                    entity.getVersionType(),
                    entity.getChangeSource(),
                    entity.getChangeDescription(),
                    entity.getWordCount(),
                    entity.getCreatedAt());
        }
    }

    public record GenerationRecordResponse(
            String id,
            String taskId,
            String documentId,
            String paragraphId,
            String generationType,
            String promptTemplateId,
            int promptTemplateVersion,
            String modelConfigId,
            String modelVersion,
            Integer materialSnapshotVersion,
            String inputContextHash,
            String outputHash,
            int durationMs,
            int tokenCountInput,
            int tokenCountOutput,
            boolean streaming,
            int retryCount,
            OffsetDateTime createdAt) {
        public static GenerationRecordResponse from(GenerationRecordEntity e) {
            return new GenerationRecordResponse(
                    e.getId(),
                    e.getTaskId(),
                    e.getDocumentId(),
                    e.getParagraphId(),
                    e.getGenerationType(),
                    e.getPromptTemplateId(),
                    e.getPromptTemplateVersion(),
                    e.getModelConfigId(),
                    e.getModelVersion(),
                    e.getMaterialSnapshotVersion(),
                    e.getInputContextHash(),
                    e.getOutputHash(),
                    e.getDurationMs(),
                    e.getTokenCountInput(),
                    e.getTokenCountOutput(),
                    Boolean.TRUE.equals(e.getIsStreaming()),
                    e.getRetryCount(),
                    e.getCreatedAt());
        }
    }

    public record DocumentResponse(
            String id,
            String taskId,
            String title,
            String contentType,
            int totalWordCount,
            String securityLevel,
            int currentVersion,
            String status,
            List<ParagraphResponse> paragraphs,
            OffsetDateTime updatedAt) {
        public static DocumentResponse from(DocumentEntity entity, List<DocumentParagraphEntity> paragraphs) {
            return new DocumentResponse(
                    entity.getId(),
                    entity.getTaskId(),
                    entity.getTitle(),
                    entity.getContentType(),
                    entity.getTotalWordCount(),
                    entity.getSecurityLevel(),
                    entity.getCurrentVersion(),
                    entity.getStatus(),
                    paragraphs.stream().map(ParagraphResponse::from).toList(),
                    entity.getUpdatedAt());
        }
    }

    private static List<String> parseIds(String value) {
        if (value == null || value.isBlank() || "[]".equals(value)) return List.of();
        return java.util.Arrays.stream(value.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
