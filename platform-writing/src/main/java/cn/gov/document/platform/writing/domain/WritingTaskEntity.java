package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("ai_writing_task")
public class WritingTaskEntity extends BaseEntity {
    private String taskName;
    private String documentTypeCode;
    private String documentCategory;
    private String writingTopic;
    private String writingPurpose;
    private String direction;
    private String targetAudience;
    private String primaryRecipient;
    private String writingTone;
    private Integer expectedLengthMin;
    private Integer expectedLengthMax;
    private LocalDate targetDate;
    private Boolean allowExternalKnowledge;
    private String securityLevel;
    private String outputTemplateId;
    private String status;
    private Integer outlineConfirmedVersion;
    private String currentDocumentId;
    private String sourceSystem;
    private String businessId;
    private String draftingDepartment;
    private String callbackUrl;

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getDocumentTypeCode() {
        return documentTypeCode;
    }

    public void setDocumentTypeCode(String value) {
        this.documentTypeCode = value;
    }

    public String getDocumentCategory() {
        return documentCategory;
    }

    public void setDocumentCategory(String value) {
        this.documentCategory = value;
    }

    public String getWritingTopic() {
        return writingTopic;
    }

    public void setWritingTopic(String value) {
        this.writingTopic = value;
    }

    public String getWritingPurpose() {
        return writingPurpose;
    }

    public void setWritingPurpose(String value) {
        this.writingPurpose = value;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String value) {
        this.direction = value;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String value) {
        this.targetAudience = value;
    }

    public String getPrimaryRecipient() {
        return primaryRecipient;
    }

    public void setPrimaryRecipient(String value) {
        this.primaryRecipient = value;
    }

    public String getWritingTone() {
        return writingTone;
    }

    public void setWritingTone(String value) {
        this.writingTone = value;
    }

    public Integer getExpectedLengthMin() {
        return expectedLengthMin;
    }

    public void setExpectedLengthMin(Integer value) {
        this.expectedLengthMin = value;
    }

    public Integer getExpectedLengthMax() {
        return expectedLengthMax;
    }

    public void setExpectedLengthMax(Integer value) {
        this.expectedLengthMax = value;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate value) {
        this.targetDate = value;
    }

    public Boolean getAllowExternalKnowledge() {
        return allowExternalKnowledge;
    }

    public void setAllowExternalKnowledge(Boolean value) {
        this.allowExternalKnowledge = value;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(String value) {
        this.securityLevel = value;
    }

    public String getOutputTemplateId() {
        return outputTemplateId;
    }

    public void setOutputTemplateId(String value) {
        this.outputTemplateId = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }

    public Integer getOutlineConfirmedVersion() {
        return outlineConfirmedVersion;
    }

    public void setOutlineConfirmedVersion(Integer value) {
        this.outlineConfirmedVersion = value;
    }

    public String getCurrentDocumentId() {
        return currentDocumentId;
    }

    public void setCurrentDocumentId(String value) {
        this.currentDocumentId = value;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String value) {
        sourceSystem = value;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String value) {
        businessId = value;
    }

    public String getDraftingDepartment() {
        return draftingDepartment;
    }

    public void setDraftingDepartment(String value) {
        draftingDepartment = value;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String value) {
        callbackUrl = value;
    }
}
