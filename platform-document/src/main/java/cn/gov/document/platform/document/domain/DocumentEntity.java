package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_document")
public class DocumentEntity extends BaseEntity {
    private String taskId;
    private String title;
    private String contentType;
    private String contentStoragePath;
    private String plainTextCache;
    private Integer totalWordCount;
    private String securityLevel;
    private Integer currentVersion;
    private String status;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String value) {
        taskId = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String value) {
        title = value;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String value) {
        contentType = value;
    }

    public String getContentStoragePath() {
        return contentStoragePath;
    }

    public void setContentStoragePath(String value) {
        contentStoragePath = value;
    }

    public String getPlainTextCache() {
        return plainTextCache;
    }

    public void setPlainTextCache(String value) {
        plainTextCache = value;
    }

    public Integer getTotalWordCount() {
        return totalWordCount;
    }

    public void setTotalWordCount(Integer value) {
        totalWordCount = value;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(String value) {
        securityLevel = value;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer value) {
        currentVersion = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }
}
