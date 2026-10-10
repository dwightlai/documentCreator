package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.WritingMaterialEntity;
import cn.gov.document.platform.writing.domain.WritingMaterialSnapshotEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class WritingMaterialDtos {
    private WritingMaterialDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 500) String materialName,
            @NotBlank String sourceType,
            String sourceId,
            @NotBlank String authorityLevel,
            @NotBlank String usageMode,
            @NotBlank String citationPurpose,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String permissionStatus,
            String selectedFragment,
            Boolean locked,
            String fileHash,
            String filePath) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 500) String materialName,
            @NotBlank String authorityLevel,
            @NotBlank String usageMode,
            @NotBlank String citationPurpose,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String permissionStatus,
            String selectedFragment,
            Boolean locked,
            String fileHash,
            String filePath,
            @NotNull Integer lockVersion) {}

    public record Response(
            String id,
            String taskId,
            String materialName,
            String sourceType,
            String sourceId,
            String authorityLevel,
            String usageMode,
            String citationPurpose,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String permissionStatus,
            String selectedFragment,
            Boolean locked,
            String fileHash,
            String filePath,
            Integer lockVersion) {
        public static Response from(WritingMaterialEntity entity) {
            return new Response(
                    entity.getId(),
                    entity.getTaskId(),
                    entity.getMaterialName(),
                    entity.getSourceType(),
                    entity.getSourceId(),
                    entity.getAuthorityLevel(),
                    entity.getUsageMode(),
                    entity.getCitationPurpose(),
                    entity.getEffectiveDate(),
                    entity.getExpiryDate(),
                    entity.getPermissionStatus(),
                    entity.getSelectedFragment(),
                    entity.getIsLocked(),
                    entity.getFileHash(),
                    entity.getFilePath(),
                    entity.getLockVersion());
        }
    }

    public record SnapshotItem(
            String materialId,
            int materialVersion,
            String fileHash,
            String selectedFragment,
            Integer knowledgeEntryVersion,
            java.time.OffsetDateTime retrievalTime,
            String permissionCheckResult) {
        public static SnapshotItem from(WritingMaterialSnapshotEntity e) {
            return new SnapshotItem(
                    e.getMaterialId(),
                    e.getMaterialVersion(),
                    e.getFileHash(),
                    e.getSelectedFragment(),
                    e.getKnowledgeEntryVersion(),
                    e.getRetrievalTime(),
                    e.getPermissionCheckResult());
        }
    }

    public record SnapshotResponse(
            String taskId, int snapshotVersion, List<SnapshotItem> materials, long allowedCount, long deniedCount) {}
}
