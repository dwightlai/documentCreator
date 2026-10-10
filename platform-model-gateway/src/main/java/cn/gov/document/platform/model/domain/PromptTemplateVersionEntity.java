package cn.gov.document.platform.model.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_prompt_template_version")
public class PromptTemplateVersionEntity extends BaseEntity {
    private String templateId, systemRole, fullPrompt, changeDescription, testResults, approvedBy;
    private Integer versionNumber;
    private OffsetDateTime approvedAt;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String v) {
        templateId = v;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer v) {
        versionNumber = v;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String v) {
        systemRole = v;
    }

    public String getFullPrompt() {
        return fullPrompt;
    }

    public void setFullPrompt(String v) {
        fullPrompt = v;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String v) {
        changeDescription = v;
    }

    public String getTestResults() {
        return testResults;
    }

    public void setTestResults(String v) {
        testResults = v;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String v) {
        approvedBy = v;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime v) {
        approvedAt = v;
    }
}
