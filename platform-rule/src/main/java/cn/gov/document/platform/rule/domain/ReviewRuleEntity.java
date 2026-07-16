package cn.gov.document.platform.rule.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("ai_review_rule")
public class ReviewRuleEntity extends BaseEntity {
    private String ruleCode; private String ruleName; private String category; private String applicableDocumentTypes;
    private String riskLevel; private String detectionMethod; private String ruleExpression; private String basis;
    private String lifecycleStatus; private LocalDate effectiveDate; private LocalDate expiryDate;
    private Integer currentVersion; private Boolean enabled;
    public String getRuleCode(){return ruleCode;} public void setRuleCode(String v){ruleCode=v;}
    public String getRuleName(){return ruleName;} public void setRuleName(String v){ruleName=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getApplicableDocumentTypes(){return applicableDocumentTypes;} public void setApplicableDocumentTypes(String v){applicableDocumentTypes=v;}
    public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String v){riskLevel=v;}
    public String getDetectionMethod(){return detectionMethod;} public void setDetectionMethod(String v){detectionMethod=v;}
    public String getRuleExpression(){return ruleExpression;} public void setRuleExpression(String v){ruleExpression=v;}
    public String getBasis(){return basis;} public void setBasis(String v){basis=v;}
    public String getLifecycleStatus(){return lifecycleStatus;} public void setLifecycleStatus(String v){lifecycleStatus=v;}
    public LocalDate getEffectiveDate(){return effectiveDate;} public void setEffectiveDate(LocalDate v){effectiveDate=v;}
    public LocalDate getExpiryDate(){return expiryDate;} public void setExpiryDate(LocalDate v){expiryDate=v;}
    public Integer getCurrentVersion(){return currentVersion;} public void setCurrentVersion(Integer v){currentVersion=v;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
}
