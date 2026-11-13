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

    public record UpdateSourceRequest(
            @NotBlank @Size(max = 200) String sourceName,
            @NotBlank String category,
            @NotBlank String storageType,
            String vectorModel,
            @Min(1) Integer vectorDimensions,
            String permissionScope,
            @NotNull Integer lockVersion) {}

    public record ReclassifySourceRequest(@NotBlank @Size(max = 100) String category, @NotNull Integer lockVersion) {}

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
            int lockVersion,
            String usageType,
            String completeness,
            String verificationStatus,
            String validityStatus,
            String publisher,
            String documentNumber,
            LocalDate issuedDate,
            String applicableRegion,
            String applicableAudience,
            String applicableScope,
            String reviewedBy,
            OffsetDateTime reviewedAt) {
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
                    e.getLockVersion(),
                    e.getUsageType(),
                    e.getCompleteness(),
                    e.getVerificationStatus(),
                    e.getValidityStatus(),
                    e.getPublisher(),
                    e.getDocumentNumber(),
                    e.getIssuedDate(),
                    e.getApplicableRegion(),
                    e.getApplicableAudience(),
                    e.getApplicableScope(),
                    e.getReviewedBy(),
                    e.getReviewedAt());
        }
    }

    public record UpdateDocumentRequest(
            @NotBlank @Size(max = 500) String documentTitle,
            String metadata,
            @NotBlank String authorityLevel,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            @NotBlank String permissionLevel,
            @NotNull Integer lockVersion,
            String usageType,
            String completeness,
            String verificationStatus,
            String validityStatus,
            String publisher,
            String documentNumber,
            LocalDate issuedDate,
            String applicableRegion,
            String applicableAudience,
            String applicableScope) {}

    public record ChunkResponse(
            String id,
            int chunkOrder,
            String chunkText,
            int startPosition,
            int endPosition,
            Integer pageNumber,
            int lockVersion) {
        public static ChunkResponse from(KnowledgeChunkEntity e) {
            return new ChunkResponse(
                    e.getId(),
                    e.getChunkOrder(),
                    e.getChunkText(),
                    e.getStartPosition(),
                    e.getEndPosition(),
                    e.getPageNumber(),
                    e.getLockVersion());
        }
    }

    public record UpdateChunkRequest(@NotBlank @Size(max = 20000) String chunkText, @NotNull Integer lockVersion) {}

    public record ParseResponse(String documentId, String status, int chunkCount, List<String> warnings) {}

    public record ReadResponse(DocumentResponse document, List<ChunkResponse> chunks) {}

    public record RevisionResponse(
            int documentVersion, String snapshotJson, String capturedBy, OffsetDateTime capturedAt) {}

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
            String fileHash,
            /** 命中方式：关键词、语义或混合。 */
            String matchType,
            String usageType,
            String completeness,
            String verificationStatus,
            String validityStatus,
            int documentVersion) {}
}
