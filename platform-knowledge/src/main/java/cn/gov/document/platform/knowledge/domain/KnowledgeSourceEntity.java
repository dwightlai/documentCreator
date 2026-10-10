package cn.gov.document.platform.knowledge.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_knowledge_source")
public class KnowledgeSourceEntity extends BaseEntity {
    private String sourceCode, sourceName, category, storageType, vectorModel, permissionScope;
    private Integer vectorDimensions, entryCount;
    private OffsetDateTime lastSyncTime;
    private Boolean isActive;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String v) {
        sourceCode = v;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String v) {
        sourceName = v;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String v) {
        category = v;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String v) {
        storageType = v;
    }

    public String getVectorModel() {
        return vectorModel;
    }

    public void setVectorModel(String v) {
        vectorModel = v;
    }

    public Integer getVectorDimensions() {
        return vectorDimensions;
    }

    public void setVectorDimensions(Integer v) {
        vectorDimensions = v;
    }

    public String getPermissionScope() {
        return permissionScope;
    }

    public void setPermissionScope(String v) {
        permissionScope = v;
    }

    public Integer getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(Integer v) {
        entryCount = v;
    }

    public OffsetDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(OffsetDateTime v) {
        lastSyncTime = v;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean v) {
        isActive = v;
    }
}
