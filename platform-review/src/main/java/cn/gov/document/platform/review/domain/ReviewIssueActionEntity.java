package cn.gov.document.platform.review.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_review_issue_action")
public class ReviewIssueActionEntity extends BaseEntity {
    private String issueId;
    private String actionType;
    private String actionDetail;
    private String operatorId;
    private String relatedIssueIds;

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String v) {
        issueId = v;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String v) {
        actionType = v;
    }

    public String getActionDetail() {
        return actionDetail;
    }

    public void setActionDetail(String v) {
        actionDetail = v;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String v) {
        operatorId = v;
    }

    public String getRelatedIssueIds() {
        return relatedIssueIds;
    }

    public void setRelatedIssueIds(String v) {
        relatedIssueIds = v;
    }
}
