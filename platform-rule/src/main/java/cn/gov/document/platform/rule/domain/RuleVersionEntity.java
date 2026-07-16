package cn.gov.document.platform.rule.domain;
import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
@TableName("ai_review_rule_version")
public class RuleVersionEntity extends BaseEntity {
    private String ruleId; private Integer versionNumber; private String ruleExpression; private String changeDescription;
    private String approvedBy; private OffsetDateTime approvedAt; private String testResults;
    public String getRuleId(){return ruleId;} public void setRuleId(String v){ruleId=v;}
    public Integer getVersionNumber(){return versionNumber;} public void setVersionNumber(Integer v){versionNumber=v;}
    public String getRuleExpression(){return ruleExpression;} public void setRuleExpression(String v){ruleExpression=v;}
    public String getChangeDescription(){return changeDescription;} public void setChangeDescription(String v){changeDescription=v;}
    public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String v){approvedBy=v;}
    public OffsetDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(OffsetDateTime v){approvedAt=v;}
    public String getTestResults(){return testResults;} public void setTestResults(String v){testResults=v;}
}
