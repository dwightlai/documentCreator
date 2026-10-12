package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/** 文稿提交 OA 的追加型记录，保存提交版本与门禁结论快照。 */
@TableName("ai_document_submission")
public class DocumentSubmissionEntity extends BaseEntity {
    private String writingTaskId;
    private String documentId;
    private Integer documentVersion;
    private String reviewTaskId;
    private String targetSystem;
    private String businessId;
    private String gateResult;
    private String callbackJobId;
    private String submittedBy;
    private OffsetDateTime submittedAt;

    public String getWritingTaskId() {
        return writingTaskId;
    }

    public void setWritingTaskId(String value) {
        writingTaskId = value;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String value) {
        documentId = value;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer value) {
        documentVersion = value;
    }

    public String getReviewTaskId() {
        return reviewTaskId;
    }

    public void setReviewTaskId(String value) {
        reviewTaskId = value;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(String value) {
        targetSystem = value;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String value) {
        businessId = value;
    }

    public String getGateResult() {
        return gateResult;
    }

    public void setGateResult(String value) {
        gateResult = value;
    }

    public String getCallbackJobId() {
        return callbackJobId;
    }

    public void setCallbackJobId(String value) {
        callbackJobId = value;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String value) {
        submittedBy = value;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime value) {
        submittedAt = value;
    }
}
