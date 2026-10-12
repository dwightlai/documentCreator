package cn.gov.document.platform.review.api;

import cn.gov.document.platform.review.domain.SubmissionGateConfigEntity;
import java.util.List;

public final class SubmissionGateDtos {
    private SubmissionGateDtos() {}

    public record Config(
            boolean requireReview,
            boolean requireLatestReview,
            boolean blockOnSevere,
            boolean requireImportantProcessed,
            boolean requireReport) {
        public static final Config DEFAULT = new Config(true, true, true, true, true);

        public static Config from(SubmissionGateConfigEntity entity) {
            if (entity == null) return DEFAULT;
            return new Config(
                    !Boolean.FALSE.equals(entity.getRequireReview()),
                    !Boolean.FALSE.equals(entity.getRequireLatestReview()),
                    !Boolean.FALSE.equals(entity.getBlockOnSevere()),
                    !Boolean.FALSE.equals(entity.getRequireImportantProcessed()),
                    !Boolean.FALSE.equals(entity.getRequireReport()));
        }
    }

    /** 单条门禁规则的检查结果。 */
    public record Rule(String code, String name, boolean passed, String message) {}

    public record CheckResult(
            String writingTaskId, Integer documentVersion, String reviewTaskId, boolean passed, List<Rule> rules) {}
}
