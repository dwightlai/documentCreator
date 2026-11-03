package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_document_citation")
public class DocumentCitationEntity extends BaseEntity {
    private String documentId;
    private String paragraphId;
    private String materialId;
    private String citationType;
    private String citedFragment;
    private Integer positionStart;
    private Integer positionEnd;
    private String displayMode;
    private String knowledgeDocumentId, knowledgeChunkId, sourceFileHash, sourceExcerpt,
            reviewStatus, confirmedBy;
    private Integer knowledgeDocumentVersion;
    private OffsetDateTime confirmedAt;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String v) {
        documentId = v;
    }

    public String getParagraphId() {
        return paragraphId;
    }

    public void setParagraphId(String v) {
        paragraphId = v;
    }

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String v) {
        materialId = v;
    }

    public String getCitationType() {
        return citationType;
    }

    public void setCitationType(String v) {
        citationType = v;
    }

    public String getCitedFragment() {
        return citedFragment;
    }

    public void setCitedFragment(String v) {
        citedFragment = v;
    }

    public Integer getPositionStart() {
        return positionStart;
    }

    public void setPositionStart(Integer v) {
        positionStart = v;
    }

    public Integer getPositionEnd() {
        return positionEnd;
    }

    public void setPositionEnd(Integer v) {
        positionEnd = v;
    }

    public String getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(String v) {
        displayMode = v;
    }

    public String getKnowledgeDocumentId() { return knowledgeDocumentId; }
    public void setKnowledgeDocumentId(String v) { knowledgeDocumentId = v; }
    public String getKnowledgeChunkId() { return knowledgeChunkId; }
    public void setKnowledgeChunkId(String v) { knowledgeChunkId = v; }
    public String getSourceFileHash() { return sourceFileHash; }
    public void setSourceFileHash(String v) { sourceFileHash = v; }
    public String getSourceExcerpt() { return sourceExcerpt; }
    public void setSourceExcerpt(String v) { sourceExcerpt = v; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String v) { reviewStatus = v; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String v) { confirmedBy = v; }
    public Integer getKnowledgeDocumentVersion() { return knowledgeDocumentVersion; }
    public void setKnowledgeDocumentVersion(Integer v) { knowledgeDocumentVersion = v; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime v) { confirmedAt = v; }
}
