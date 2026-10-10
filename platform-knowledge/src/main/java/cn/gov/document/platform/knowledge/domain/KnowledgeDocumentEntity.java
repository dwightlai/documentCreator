package cn.gov.document.platform.knowledge.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("ai_knowledge_document")
public class KnowledgeDocumentEntity extends BaseEntity {
    private String sourceId,
            documentTitle,
            originalFilePath,
            originalFileHash,
            parsedStatus,
            metadata,
            authorityLevel,
            permissionLevel,
            extractedText;
    private LocalDate effectiveDate, expiryDate;
    private Integer chunkCount, documentVersion;

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String v) {
        sourceId = v;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String v) {
        documentTitle = v;
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String v) {
        originalFilePath = v;
    }

    public String getOriginalFileHash() {
        return originalFileHash;
    }

    public void setOriginalFileHash(String v) {
        originalFileHash = v;
    }

    public String getParsedStatus() {
        return parsedStatus;
    }

    public void setParsedStatus(String v) {
        parsedStatus = v;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String v) {
        metadata = v;
    }

    public String getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(String v) {
        authorityLevel = v;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate v) {
        effectiveDate = v;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate v) {
        expiryDate = v;
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(String v) {
        permissionLevel = v;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer v) {
        chunkCount = v;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer v) {
        documentVersion = v;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String v) {
        extractedText = v;
    }
}
