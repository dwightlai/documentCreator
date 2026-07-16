package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

@TableName("ai_writing_material")
public class WritingMaterialEntity extends BaseEntity {
    private String taskId;
    private String materialName;
    private String sourceType;
    private String sourceId;
    private String authorityLevel;
    private String usageMode;
    private String citationPurpose;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String permissionStatus;
    private String selectedFragment;
    private Boolean isLocked;
    private String fileHash;
    private String filePath;

    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String value) { this.materialName = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { this.sourceType = value; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String value) { this.sourceId = value; }
    public String getAuthorityLevel() { return authorityLevel; }
    public void setAuthorityLevel(String value) { this.authorityLevel = value; }
    public String getUsageMode() { return usageMode; }
    public void setUsageMode(String value) { this.usageMode = value; }
    public String getCitationPurpose() { return citationPurpose; }
    public void setCitationPurpose(String value) { this.citationPurpose = value; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate value) { this.effectiveDate = value; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate value) { this.expiryDate = value; }
    public String getPermissionStatus() { return permissionStatus; }
    public void setPermissionStatus(String value) { this.permissionStatus = value; }
    public String getSelectedFragment() { return selectedFragment; }
    public void setSelectedFragment(String value) { this.selectedFragment = value; }
    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean value) { this.isLocked = value; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String value) { this.fileHash = value; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String value) { this.filePath = value; }
}
