package cn.gov.document.platform.rule.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("ai_review_term")
public class ReviewTermEntity extends BaseEntity {
    private String dictionaryId;
    private String termText;
    private String standardForm;
    private String aliases;
    private String categoryTag;
    private String riskLevel;
    private String sourceReference;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String usageNote;

    public String getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(String v) {
        dictionaryId = v;
    }

    public String getTermText() {
        return termText;
    }

    public void setTermText(String v) {
        termText = v;
    }

    public String getStandardForm() {
        return standardForm;
    }

    public void setStandardForm(String v) {
        standardForm = v;
    }

    public String getAliases() {
        return aliases;
    }

    public void setAliases(String v) {
        aliases = v;
    }

    public String getCategoryTag() {
        return categoryTag;
    }

    public void setCategoryTag(String v) {
        categoryTag = v;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String v) {
        riskLevel = v;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String v) {
        sourceReference = v;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate v) {
        effectiveDate = v;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate v) {
        expiryDate = v;
    }

    public String getUsageNote() {
        return usageNote;
    }

    public void setUsageNote(String v) {
        usageNote = v;
    }
}
