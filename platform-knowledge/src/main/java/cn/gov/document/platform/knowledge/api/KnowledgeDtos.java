package cn.gov.document.platform.knowledge.api;

import cn.gov.document.platform.knowledge.domain.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class KnowledgeDtos {
    private KnowledgeDtos() {}

    public record CreateSourceRequest(
            @NotBlank @Size(max = 50) String sourceCode,
            @NotBlank @Size(max = 200) String sourceName,
            @NotBlank String category,
            @NotBlank String storageType,
            String vectorModel,
            @Min(1) Integer vectorDimensions,
            String permissionScope) {}

    public record SourceResponse(
            String id,
            String sourceCode,
            String sourceName,
            String category,
            String storageType,
            String vectorModel,
            Integer vectorDimensions,
            String permissionScope,
            int entryCount,
            OffsetDateTime lastSyncTime,
            boolean active,
            int lockVersion) {
        public static SourceResponse from(KnowledgeSourceEntity e) {
            return new SourceResponse(
                    e.getId(),
                    e.getSourceCode(),
                    e.getSourceName(),
                    e.getCategory(),
                    e.getStorageType(),
                    e.getVectorModel(),
                    e.getVectorDimensions(),
                    e.getPermissionScope(),
                    e.getEntryCount(),
                    e.getLastSyncTime(),
                    Boolean.TRUE.equals(e.getIsActive()),
                    e.getLockVersion());
        }
    }

    public record DocumentResponse(
            String id,
            String sourceId,
            String documentTitle,
            String originalFilePath,
            String originalFileHash,
            String parsedStatus,
            String metadata,
            String authorityLevel,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String permissionLevel,
            int chunkCount,
            int documentVersion,
            int lockVersion) {
        public static DocumentResponse from(KnowledgeDocumentEntity e) {
            return new DocumentResponse(
                    e.getId(),
                    e.getSourceId(),
                    e.getDocumentTitle(),
                    e.getOriginalFilePath(),
                    e.getOriginalFileHash(),
                    e.getParsedStatus(),
                    e.getMetadata(),
                    e.getAuthorityLevel(),
                    e.getEffectiveDate(),
                    e.getExpiryDate(),
                    e.getPermissionLevel(),
                    e.getChunkCount(),
                    e.getDocumentVersion(),
                    e.getLockVersion());
        }
    }

    public record ParseResponse(String documentId, String status, int chunkCount, List<String> warnings) {}

    public record SearchResponse(
            String sourceId,
            String sourceName,
            String documentId,
            String documentTitle,
            String chunkId,
            int chunkOrder,
            String snippet,
            String authorityLevel,
            String permissionLevel,
            double score,
            String fileHash) {}
}
