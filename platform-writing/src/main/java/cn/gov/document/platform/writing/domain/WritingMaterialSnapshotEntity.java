package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_writing_material_snapshot")
public class WritingMaterialSnapshotEntity extends BaseEntity {
    private String taskId;
    private Integer snapshotVersion;
    private String materialId;
    private Integer materialVersion;
    private String fileHash;
    private String selectedFragment;
    private Integer knowledgeEntryVersion;
    private OffsetDateTime retrievalTime;
    private String permissionCheckResult;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String v) {
        taskId = v;
    }

    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Integer v) {
        snapshotVersion = v;
    }

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String v) {
        materialId = v;
    }

    public Integer getMaterialVersion() {
        return materialVersion;
    }

    public void setMaterialVersion(Integer v) {
        materialVersion = v;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String v) {
        fileHash = v;
    }

    public String getSelectedFragment() {
        return selectedFragment;
    }

    public void setSelectedFragment(String v) {
        selectedFragment = v;
    }

    public Integer getKnowledgeEntryVersion() {
        return knowledgeEntryVersion;
    }

    public void setKnowledgeEntryVersion(Integer v) {
        knowledgeEntryVersion = v;
    }

    public OffsetDateTime getRetrievalTime() {
        return retrievalTime;
    }

    public void setRetrievalTime(OffsetDateTime v) {
        retrievalTime = v;
    }

    public String getPermissionCheckResult() {
        return permissionCheckResult;
    }

    public void setPermissionCheckResult(String v) {
        permissionCheckResult = v;
    }
}
