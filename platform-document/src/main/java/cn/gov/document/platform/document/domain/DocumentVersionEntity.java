package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_document_version")
public class DocumentVersionEntity extends BaseEntity {
    private String documentId;
    private Integer versionNumber;
    private String versionType;
    private String changeSource;
    private String changeDescription;
    private String contentStoragePath;
    private Integer wordCount;
    private Boolean isRetained;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String value) {
        documentId = value;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer value) {
        versionNumber = value;
    }

    public String getVersionType() {
        return versionType;
    }

    public void setVersionType(String value) {
        versionType = value;
    }

    public String getChangeSource() {
        return changeSource;
    }

    public void setChangeSource(String value) {
        changeSource = value;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String value) {
        changeDescription = value;
    }

    public String getContentStoragePath() {
        return contentStoragePath;
    }

    public void setContentStoragePath(String value) {
        contentStoragePath = value;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer value) {
        wordCount = value;
    }

    public Boolean getIsRetained() {
        return isRetained;
    }

    public void setIsRetained(Boolean value) {
        isRetained = value;
    }
}
