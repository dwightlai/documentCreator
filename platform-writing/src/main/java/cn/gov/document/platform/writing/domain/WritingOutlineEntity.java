package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("ai_writing_outline")
public class WritingOutlineEntity extends BaseEntity {
    private String taskId;
    private Integer outlineVersion;
    private Boolean isConfirmed;
    private String confirmedBy;
    private OffsetDateTime confirmedAt;
    private String structureCheckResult;

    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { this.taskId = value; }
    public Integer getOutlineVersion() { return outlineVersion; }
    public void setOutlineVersion(Integer value) { this.outlineVersion = value; }
    public Boolean getIsConfirmed() { return isConfirmed; }
    public void setIsConfirmed(Boolean value) { this.isConfirmed = value; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String value) { this.confirmedBy = value; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime value) { this.confirmedAt = value; }
    public String getStructureCheckResult() { return structureCheckResult; }
    public void setStructureCheckResult(String value) { this.structureCheckResult = value; }
}
