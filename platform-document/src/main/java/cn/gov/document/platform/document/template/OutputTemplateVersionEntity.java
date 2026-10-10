package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_output_template_version")
public class OutputTemplateVersionEntity extends BaseEntity {
    private String templateId;
    private Integer versionNumber;
    private String templateFilePath;
    private String templateFileHash;
    private String changeNote;
    private String lifecycleStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String value) {
        templateId = value;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer value) {
        versionNumber = value;
    }

    public String getTemplateFilePath() {
        return templateFilePath;
    }

    public void setTemplateFilePath(String value) {
        templateFilePath = value;
    }

    public String getTemplateFileHash() {
        return templateFileHash;
    }

    public void setTemplateFileHash(String value) {
        templateFileHash = value;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String value) {
        changeNote = value;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String value) {
        lifecycleStatus = value;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String value) {
        approvedBy = value;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime value) {
        approvedAt = value;
    }
}
