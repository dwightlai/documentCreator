package cn.gov.document.platform.review.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_review_report")
public class ReviewReportEntity extends BaseEntity {
    private String reviewTaskId, reportStoragePath;
    private Integer reportVersion,
            totalIssues,
            severeCount,
            importantCount,
            normalCount,
            tipCount,
            adoptedCount,
            ignoredCount,
            pendingCount;
    private OffsetDateTime generatedAt;
    private Boolean isFinal;

    public String getReviewTaskId() {
        return reviewTaskId;
    }

    public void setReviewTaskId(String v) {
        reviewTaskId = v;
    }

    public Integer getReportVersion() {
        return reportVersion;
    }

    public void setReportVersion(Integer v) {
        reportVersion = v;
    }

    public Integer getTotalIssues() {
        return totalIssues;
    }

    public void setTotalIssues(Integer v) {
        totalIssues = v;
    }

    public Integer getSevereCount() {
        return severeCount;
    }

    public void setSevereCount(Integer v) {
        severeCount = v;
    }

    public Integer getImportantCount() {
        return importantCount;
    }

    public void setImportantCount(Integer v) {
        importantCount = v;
    }

    public Integer getNormalCount() {
        return normalCount;
    }

    public void setNormalCount(Integer v) {
        normalCount = v;
    }

    public Integer getTipCount() {
        return tipCount;
    }

    public void setTipCount(Integer v) {
        tipCount = v;
    }

    public Integer getAdoptedCount() {
        return adoptedCount;
    }

    public void setAdoptedCount(Integer v) {
        adoptedCount = v;
    }

    public Integer getIgnoredCount() {
        return ignoredCount;
    }

    public void setIgnoredCount(Integer v) {
        ignoredCount = v;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Integer v) {
        pendingCount = v;
    }

    public String getReportStoragePath() {
        return reportStoragePath;
    }

    public void setReportStoragePath(String v) {
        reportStoragePath = v;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime v) {
        generatedAt = v;
    }

    public Boolean getIsFinal() {
        return isFinal;
    }

    public void setIsFinal(Boolean v) {
        isFinal = v;
    }
}
