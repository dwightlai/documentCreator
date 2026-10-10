package cn.gov.document.platform.job.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.OffsetDateTime;

@TableName("ai_async_job")
public class AsyncJobEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String tenantId;
    private String jobType;
    private String businessId;
    private String payload;
    private String status;
    private Integer progress;
    private String resultSummary;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private OffsetDateTime nextRetryAt;
    private Boolean cancelRequested;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    @Version
    private Integer lockVersion;

    public String getId() {
        return id;
    }

    public void setId(String value) {
        id = value;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String value) {
        tenantId = value;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String value) {
        jobType = value;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String value) {
        businessId = value;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String value) {
        payload = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer value) {
        progress = value;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String value) {
        resultSummary = value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String value) {
        errorMessage = value;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer value) {
        retryCount = value;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer value) {
        maxRetries = value;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime value) {
        nextRetryAt = value;
    }

    public Boolean getCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(Boolean value) {
        cancelRequested = value;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String value) {
        createdBy = value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime value) {
        createdAt = value;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime value) {
        startedAt = value;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime value) {
        finishedAt = value;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer value) {
        lockVersion = value;
    }
}
