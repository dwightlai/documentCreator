package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/** 修订模式下的段落修订建议：记录提出时的原文快照与修订内容，由他人或本人逐条接受、拒绝。 */
@TableName("ai_document_suggestion")
public class DocumentSuggestionEntity extends BaseEntity {
    private String documentId;
    private String baseParagraphId;
    private Integer baseVersion;
    private String outlineNodeId;
    private Integer paragraphOrder;
    private String basePlainText;
    private String baseRichContent;
    private String proposedPlainText;
    private String proposedRichContent;
    private String adoptedGenerationIds;
    private String status;
    private String proposerId;
    private String decidedBy;
    private OffsetDateTime decidedAt;
    private String decisionNote;
    private Integer acceptedVersion;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String v) {
        documentId = v;
    }

    public String getBaseParagraphId() {
        return baseParagraphId;
    }

    public void setBaseParagraphId(String v) {
        baseParagraphId = v;
    }

    public Integer getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(Integer v) {
        baseVersion = v;
    }

    public String getOutlineNodeId() {
        return outlineNodeId;
    }

    public void setOutlineNodeId(String v) {
        outlineNodeId = v;
    }

    public Integer getParagraphOrder() {
        return paragraphOrder;
    }

    public void setParagraphOrder(Integer v) {
        paragraphOrder = v;
    }

    public String getBasePlainText() {
        return basePlainText;
    }

    public void setBasePlainText(String v) {
        basePlainText = v;
    }

    public String getBaseRichContent() {
        return baseRichContent;
    }

    public void setBaseRichContent(String v) {
        baseRichContent = v;
    }

    public String getProposedPlainText() {
        return proposedPlainText;
    }

    public void setProposedPlainText(String v) {
        proposedPlainText = v;
    }

    public String getProposedRichContent() {
        return proposedRichContent;
    }

    public void setProposedRichContent(String v) {
        proposedRichContent = v;
    }

    /** 修订中采用的划词改写候选（JSON 数组），接受时据此把段落标记为 AI 改写。 */
    public String getAdoptedGenerationIds() {
        return adoptedGenerationIds;
    }

    public void setAdoptedGenerationIds(String v) {
        adoptedGenerationIds = v;
    }

    /** 待处理、已接受、已拒绝。 */
    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public String getProposerId() {
        return proposerId;
    }

    public void setProposerId(String v) {
        proposerId = v;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String v) {
        decidedBy = v;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime v) {
        decidedAt = v;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String v) {
        decisionNote = v;
    }

    public Integer getAcceptedVersion() {
        return acceptedVersion;
    }

    public void setAcceptedVersion(Integer v) {
        acceptedVersion = v;
    }
}
