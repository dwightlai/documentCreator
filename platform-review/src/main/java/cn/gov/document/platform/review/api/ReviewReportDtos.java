package cn.gov.document.platform.review.api;

import cn.gov.document.platform.review.domain.ReviewReportEntity;
import java.time.OffsetDateTime;

public final class ReviewReportDtos {
    private ReviewReportDtos() {}

    public record Response(
            String id,
            String reviewTaskId,
            int reportVersion,
            int totalIssues,
            int severeCount,
            int importantCount,
            int normalCount,
            int tipCount,
            int adoptedCount,
            int ignoredCount,
            int pendingCount,
            boolean finalReport,
            OffsetDateTime generatedAt) {
        public static Response from(ReviewReportEntity e) {
            return new Response(
                    e.getId(),
                    e.getReviewTaskId(),
                    e.getReportVersion(),
                    e.getTotalIssues(),
                    e.getSevereCount(),
                    e.getImportantCount(),
                    e.getNormalCount(),
                    e.getTipCount(),
                    e.getAdoptedCount(),
                    e.getIgnoredCount(),
                    e.getPendingCount(),
                    Boolean.TRUE.equals(e.getIsFinal()),
                    e.getGeneratedAt());
        }
    }
}
