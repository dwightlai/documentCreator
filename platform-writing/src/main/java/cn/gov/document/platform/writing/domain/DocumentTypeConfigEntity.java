package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_document_type_config")
public class DocumentTypeConfigEntity extends BaseEntity {
    private String typeCode;
    private String typeName;
    private String documentCategory;
    private String applicableScenario;
    private String direction;
    private String requiredFields;
    private String recommendedStructure;
    private String forbiddenPatterns;
    private String closingPatterns;
    private Integer currentVersion;
    private String lifecycleStatus;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String v) {
        typeCode = v;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String v) {
        typeName = v;
    }

    public String getDocumentCategory() {
        return documentCategory;
    }

    public void setDocumentCategory(String v) {
        documentCategory = v;
    }

    public String getApplicableScenario() {
        return applicableScenario;
    }

    public void setApplicableScenario(String v) {
        applicableScenario = v;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String v) {
        direction = v;
    }

    public String getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(String v) {
        requiredFields = v;
    }

    public String getRecommendedStructure() {
        return recommendedStructure;
    }

    public void setRecommendedStructure(String v) {
        recommendedStructure = v;
    }

    public String getForbiddenPatterns() {
        return forbiddenPatterns;
    }

    public void setForbiddenPatterns(String v) {
        forbiddenPatterns = v;
    }

    public String getClosingPatterns() {
        return closingPatterns;
    }

    public void setClosingPatterns(String v) {
        closingPatterns = v;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer v) {
        currentVersion = v;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String v) {
        lifecycleStatus = v;
    }
}
