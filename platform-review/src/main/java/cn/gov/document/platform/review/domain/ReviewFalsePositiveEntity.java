package cn.gov.document.platform.review.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_review_false_positive")
public class ReviewFalsePositiveEntity extends BaseEntity {
    private String issueId,
            ruleId,
            originalText,
            suggestedFix,
            reason,
            submitterId,
            reviewStatus,
            reviewedBy,
            optimizationSuggestion;

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String v) {
        issueId = v;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String v) {
        ruleId = v;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String v) {
        originalText = v;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public void setSuggestedFix(String v) {
        suggestedFix = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        reason = v;
    }

    public String getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(String v) {
        submitterId = v;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String v) {
        reviewStatus = v;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String v) {
        reviewedBy = v;
    }

    public String getOptimizationSuggestion() {
        return optimizationSuggestion;
    }

    public void setOptimizationSuggestion(String v) {
        optimizationSuggestion = v;
    }
}
