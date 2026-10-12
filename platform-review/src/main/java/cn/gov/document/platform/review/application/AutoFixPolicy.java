package cn.gov.document.platform.review.application;

import cn.gov.document.platform.review.domain.ReviewIssueEntity;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 自动修复策略（§21）：只有带明确建议文本的文字、规范类问题允许一键采纳；事实与数据（L5）、逻辑（L6）、 内容安全（L7）问题一律禁止自动修改，必须由人工处理。
 */
public final class AutoFixPolicy {
    private static final Set<String> MANUAL_ONLY_LEVELS = Set.of("L5", "L6", "L7");

    private AutoFixPolicy() {}

    public static boolean allows(ReviewIssueEntity issue) {
        return StringUtils.hasText(issue.getSuggestedFix()) && !isManualOnly(issue.getDetectionMethod());
    }

    public static boolean isManualOnly(String detectionMethod) {
        if (detectionMethod == null) return false;
        return MANUAL_ONLY_LEVELS.stream().anyMatch(detectionMethod::endsWith);
    }
}
