package cn.gov.document.platform.review.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_review_task")
public class ReviewTaskEntity extends BaseEntity {
    private String sourceSystem;
    private String businessId;
    private String documentId;
    private String documentFileId;
    private String documentFileHash;
    private String documentTypeCode;
    private String reviewPlanId;
    private String writingTaskId;
    private String outputTemplateId;
    private String status;
    private String initiatorId;
    private String callbackUrl;
    private Integer documentVersion;
    private String sourceFileName;
    private String editedFileId;
    private Integer editVersion;
    private String reviewTitle;

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String v) {
        sourceSystem = v;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String v) {
        businessId = v;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String v) {
        documentId = v;
    }

    public String getDocumentFileId() {
        return documentFileId;
    }

    public void setDocumentFileId(String v) {
        documentFileId = v;
    }

    public String getDocumentFileHash() {
        return documentFileHash;
    }

    public void setDocumentFileHash(String v) {
        documentFileHash = v;
    }

    public String getDocumentTypeCode() {
        return documentTypeCode;
    }

    public void setDocumentTypeCode(String v) {
        documentTypeCode = v;
    }

    public String getReviewPlanId() {
        return reviewPlanId;
    }

    public void setReviewPlanId(String v) {
        reviewPlanId = v;
    }

    public String getWritingTaskId() {
        return writingTaskId;
    }

    public void setWritingTaskId(String v) {
        writingTaskId = v;
    }

    public String getOutputTemplateId() {
        return outputTemplateId;
    }

    public void setOutputTemplateId(String v) {
        outputTemplateId = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(String v) {
        initiatorId = v;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String v) {
        callbackUrl = v;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer value) {
        documentVersion = value;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String value) {
        sourceFileName = value;
    }

    public String getEditedFileId() { return editedFileId; }
    public void setEditedFileId(String value) { editedFileId = value; }
    public Integer getEditVersion() { return editVersion; }
    public void setEditVersion(Integer value) { editVersion = value; }
    public String getReviewTitle() { return reviewTitle; }
    public void setReviewTitle(String value) { reviewTitle = value; }
}
