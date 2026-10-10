package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_output_template")
public class OutputTemplateEntity extends BaseEntity {
    private String templateCode;
    private String templateName;
    private String documentTypeCode;
    private String templateFilePath;
    private String templateFileHash;
    private Boolean hasRedHeader;
    private Boolean hasFooterMark;
    private String placeholderMap;
    private String direction;
    private String description;
    private Integer currentVersion;
    private String lifecycleStatus;

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String value) {
        templateCode = value;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String value) {
        templateName = value;
    }

    public String getDocumentTypeCode() {
        return documentTypeCode;
    }

    public void setDocumentTypeCode(String value) {
        documentTypeCode = value;
    }

    public String getTemplateFilePath() {
        return templateFilePath;
    }

    public void setTemplateFilePath(String value) {
        templateFilePath = value;
    }

    public String getTemplateFileHash() {
        return templateFileHash;
    }

    public void setTemplateFileHash(String value) {
        templateFileHash = value;
    }

    public Boolean getHasRedHeader() {
        return hasRedHeader;
    }

    public void setHasRedHeader(Boolean value) {
        hasRedHeader = value;
    }

    public Boolean getHasFooterMark() {
        return hasFooterMark;
    }

    public void setHasFooterMark(Boolean value) {
        hasFooterMark = value;
    }

    public String getPlaceholderMap() {
        return placeholderMap;
    }

    public void setPlaceholderMap(String value) {
        placeholderMap = value;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String value) {
        direction = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer value) {
        currentVersion = value;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String value) {
        lifecycleStatus = value;
    }
}
