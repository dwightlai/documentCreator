package cn.gov.document.platform.job.api;

import cn.gov.document.platform.job.domain.AsyncJobEntity;
import cn.gov.document.platform.job.domain.AsyncJobEventEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class AsyncJobDtos {
    private AsyncJobDtos() {}

    public record SubmitRequest(
            @NotBlank @Size(max = 50) String jobType,
            @Size(max = 100) String businessId,
            @Size(max = 100_000) String payload,
            @Min(0) @Max(10) Integer maxRetries) {}

    public record Response(
            String id,
            String jobType,
            String businessId,
            String status,
            int progress,
            String resultSummary,
            String errorMessage,
            int retryCount,
            int maxRetries,
            boolean cancelRequested,
            OffsetDateTime createdAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt) {
        public static Response from(AsyncJobEntity entity) {
            return new Response(
                    entity.getId(),
                    entity.getJobType(),
                    entity.getBusinessId(),
                    entity.getStatus(),
                    entity.getProgress(),
                    entity.getResultSummary(),
                    entity.getErrorMessage(),
                    entity.getRetryCount(),
                    entity.getMaxRetries(),
                    Boolean.TRUE.equals(entity.getCancelRequested()),
                    entity.getCreatedAt(),
                    entity.getStartedAt(),
                    entity.getFinishedAt());
        }
    }

    public record EventResponse(int id, String event, String data, OffsetDateTime occurredAt) {
        public static EventResponse from(AsyncJobEventEntity entity) {
            return new EventResponse(
                    entity.getEventSequence(), entity.getEventType(), entity.getEventData(), entity.getOccurredAt());
        }
    }
}
