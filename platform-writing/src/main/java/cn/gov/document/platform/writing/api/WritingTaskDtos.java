package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class WritingTaskDtos {
    private WritingTaskDtos() {
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String taskName,
        @NotBlank @Size(max = 20) String documentTypeCode,
        @NotBlank String documentCategory,
        @NotBlank @Size(max = 500) String writingTopic,
        String writingPurpose,
        String direction,
        String targetAudience,
        String primaryRecipient,
        String writingTone,
        @Min(1) @Max(100000) Integer expectedLengthMin,
        @Min(1) @Max(100000) Integer expectedLengthMax,
        LocalDate targetDate,
        Boolean allowExternalKnowledge,
        String securityLevel,
        String outputTemplateId
    ) {
        @AssertTrue(message = "期望最大字数不得小于最小字数")
        public boolean isLengthRangeValid() {
            return expectedLengthMin == null || expectedLengthMax == null || expectedLengthMax >= expectedLengthMin;
        }
    }

    public record UpdateRequest(
        @NotBlank @Size(max = 200) String taskName,
        @NotBlank @Size(max = 20) String documentTypeCode,
        @NotBlank String documentCategory,
        @NotBlank @Size(max = 500) String writingTopic,
        String writingPurpose,
        String direction,
        String targetAudience,
        String primaryRecipient,
        String writingTone,
        @Min(1) @Max(100000) Integer expectedLengthMin,
        @Min(1) @Max(100000) Integer expectedLengthMax,
        LocalDate targetDate,
        Boolean allowExternalKnowledge,
        String securityLevel,
        String outputTemplateId,
        @NotNull Integer lockVersion
    ) {
        @AssertTrue(message = "期望最大字数不得小于最小字数")
        public boolean isLengthRangeValid() {
            return expectedLengthMin == null || expectedLengthMax == null || expectedLengthMax >= expectedLengthMin;
        }
    }

    public record StatusRequest(@NotBlank String status, @NotNull Integer lockVersion, String reason) {
    }

    public record Response(
        String id,
        String taskName,
        String documentTypeCode,
        String documentCategory,
        String writingTopic,
        String writingPurpose,
        String direction,
        String targetAudience,
        String primaryRecipient,
        String writingTone,
        Integer expectedLengthMin,
        Integer expectedLengthMax,
        LocalDate targetDate,
        Boolean allowExternalKnowledge,
        String securityLevel,
        String outputTemplateId,
        String status,
        Integer outlineConfirmedVersion,
        String currentDocumentId,
        Integer lockVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        public static Response from(WritingTaskEntity entity) {
            return new Response(entity.getId(), entity.getTaskName(), entity.getDocumentTypeCode(),
                entity.getDocumentCategory(), entity.getWritingTopic(), entity.getWritingPurpose(), entity.getDirection(),
                entity.getTargetAudience(), entity.getPrimaryRecipient(), entity.getWritingTone(),
                entity.getExpectedLengthMin(), entity.getExpectedLengthMax(), entity.getTargetDate(),
                entity.getAllowExternalKnowledge(), entity.getSecurityLevel(), entity.getOutputTemplateId(),
                entity.getStatus(), entity.getOutlineConfirmedVersion(), entity.getCurrentDocumentId(),
                entity.getLockVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }
}
