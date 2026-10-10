package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_writing_idea")
public class WritingIdeaEntity extends BaseEntity {
    private String taskId;
    private Integer version;
    private String mainTheme;
    private String coreViewpoints;
    private String writingSequence;
    private String mustExpressFacts;
    private String optionalContent;
    private String missingMaterials;
    private String riskWarnings;
    private Boolean isConfirmed;
    private String confirmedBy;
    private OffsetDateTime confirmedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String value) {
        taskId = value;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer value) {
        version = value;
    }

    public String getMainTheme() {
        return mainTheme;
    }

    public void setMainTheme(String value) {
        mainTheme = value;
    }

    public String getCoreViewpoints() {
        return coreViewpoints;
    }

    public void setCoreViewpoints(String value) {
        coreViewpoints = value;
    }

    public String getWritingSequence() {
        return writingSequence;
    }

    public void setWritingSequence(String value) {
        writingSequence = value;
    }

    public String getMustExpressFacts() {
        return mustExpressFacts;
    }

    public void setMustExpressFacts(String value) {
        mustExpressFacts = value;
    }

    public String getOptionalContent() {
        return optionalContent;
    }

    public void setOptionalContent(String value) {
        optionalContent = value;
    }

    public String getMissingMaterials() {
        return missingMaterials;
    }

    public void setMissingMaterials(String value) {
        missingMaterials = value;
    }

    public String getRiskWarnings() {
        return riskWarnings;
    }

    public void setRiskWarnings(String value) {
        riskWarnings = value;
    }

    public Boolean getIsConfirmed() {
        return isConfirmed;
    }

    public void setIsConfirmed(Boolean value) {
        isConfirmed = value;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String value) {
        confirmedBy = value;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime value) {
        confirmedAt = value;
    }
}
