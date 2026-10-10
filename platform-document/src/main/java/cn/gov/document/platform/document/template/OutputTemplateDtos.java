package cn.gov.document.platform.document.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class OutputTemplateDtos {
    private OutputTemplateDtos() {}

    public record CreateMetadata(
            @NotBlank @Size(max = 50) String templateCode,
            @NotBlank @Size(max = 200) String templateName,
            @NotBlank @Size(max = 20) String documentTypeCode,
            boolean hasRedHeader,
            boolean hasFooterMark,
            @Size(max = 10) String direction,
            @Size(max = 5000) String description) {}

    public record UpdateMetadata(
            @NotBlank @Size(max = 200) String templateName,
            @NotBlank @Size(max = 20) String documentTypeCode,
            boolean hasRedHeader,
            boolean hasFooterMark,
            @Size(max = 10) String direction,
            @Size(max = 5000) String description,
            @NotNull Integer lockVersion) {}

    public record RenderRequest(Map<String, String> values, List<String> sections) {}

    public record Response(
            String id,
            String templateCode,
            String templateName,
            String documentTypeCode,
            String templateFileHash,
            boolean hasRedHeader,
            boolean hasFooterMark,
            Map<String, String> placeholderMap,
            String direction,
            String description,
            int currentVersion,
            String lifecycleStatus,
            int lockVersion,
            OffsetDateTime updatedAt) {}

    public record VersionResponse(
            String id,
            int versionNumber,
            String templateFileHash,
            String changeNote,
            String lifecycleStatus,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime createdAt) {}
}
