package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_writing_knowledge_selection")
public class WritingKnowledgeSelectionEntity extends BaseEntity {
    private String taskId,
            outlineNodeId,
            sourceId,
            knowledgeDocumentId,
            chunkId,
            usageType,
            documentTitle,
            excerpt,
            fileHash,
            selectedBy,
            selectionReason,
            completeness;
    private Integer documentVersion;
    private OffsetDateTime selectedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String v) {
        taskId = v;
    }

    public String getOutlineNodeId() {
        return outlineNodeId;
    }

    public void setOutlineNodeId(String v) {
        outlineNodeId = v;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String v) {
        sourceId = v;
    }

    public String getKnowledgeDocumentId() {
        return knowledgeDocumentId;
    }

    public void setKnowledgeDocumentId(String v) {
        knowledgeDocumentId = v;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String v) {
        chunkId = v;
    }

    public String getUsageType() {
        return usageType;
    }

    public void setUsageType(String v) {
        usageType = v;
    }

    public String getCompleteness() {
        return completeness;
    }

    public void setCompleteness(String v) {
        completeness = v;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String v) {
        documentTitle = v;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String v) {
        excerpt = v;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String v) {
        fileHash = v;
    }

    public String getSelectedBy() {
        return selectedBy;
    }

    public void setSelectedBy(String v) {
        selectedBy = v;
    }

    public String getSelectionReason() {
        return selectionReason;
    }

    public void setSelectionReason(String v) {
        selectionReason = v;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer v) {
        documentVersion = v;
    }

    public OffsetDateTime getSelectedAt() {
        return selectedAt;
    }

    public void setSelectedAt(OffsetDateTime v) {
        selectedAt = v;
    }
}
