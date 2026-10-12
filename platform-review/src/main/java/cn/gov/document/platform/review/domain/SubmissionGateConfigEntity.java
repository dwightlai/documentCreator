package cn.gov.document.platform.review.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

/** §31.3 提交门禁的单位级配置；未配置时使用全部开启的默认值。 */
@TableName("ai_submission_gate_config")
public class SubmissionGateConfigEntity extends BaseEntity {
    private Boolean requireReview;
    private Boolean requireLatestReview;
    private Boolean blockOnSevere;
    private Boolean requireImportantProcessed;
    private Boolean requireReport;
    /** 允许清空：null 也要写回数据库。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String requiredSubmitRole;

    public String getRequiredSubmitRole() {
        return requiredSubmitRole;
    }

    public void setRequiredSubmitRole(String value) {
        requiredSubmitRole = value;
    }

    public Boolean getRequireReview() {
        return requireReview;
    }

    public void setRequireReview(Boolean value) {
        requireReview = value;
    }

    public Boolean getRequireLatestReview() {
        return requireLatestReview;
    }

    public void setRequireLatestReview(Boolean value) {
        requireLatestReview = value;
    }

    public Boolean getBlockOnSevere() {
        return blockOnSevere;
    }

    public void setBlockOnSevere(Boolean value) {
        blockOnSevere = value;
    }

    public Boolean getRequireImportantProcessed() {
        return requireImportantProcessed;
    }

    public void setRequireImportantProcessed(Boolean value) {
        requireImportantProcessed = value;
    }

    public Boolean getRequireReport() {
        return requireReport;
    }

    public void setRequireReport(Boolean value) {
        requireReport = value;
    }
}
