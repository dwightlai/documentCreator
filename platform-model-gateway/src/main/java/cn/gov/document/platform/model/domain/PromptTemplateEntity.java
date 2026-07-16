package cn.gov.document.platform.model.domain;
import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("ai_prompt_template") public class PromptTemplateEntity extends BaseEntity {
    private String templateCode,templateName,applicableScene,documentTypeCode,applicableModels,systemRole,documentTypeMethod,inputFields,
        materialCitationRule,antiFabricationRule,outputFormat,riskConstraint,modelParameters,lifecycleStatus,testSampleIds;
    private Integer currentVersion;
    public String getTemplateCode(){return templateCode;}public void setTemplateCode(String v){templateCode=v;}public String getTemplateName(){return templateName;}public void setTemplateName(String v){templateName=v;}
    public String getApplicableScene(){return applicableScene;}public void setApplicableScene(String v){applicableScene=v;}public String getDocumentTypeCode(){return documentTypeCode;}public void setDocumentTypeCode(String v){documentTypeCode=v;}
    public String getApplicableModels(){return applicableModels;}public void setApplicableModels(String v){applicableModels=v;}public String getSystemRole(){return systemRole;}public void setSystemRole(String v){systemRole=v;}
    public String getDocumentTypeMethod(){return documentTypeMethod;}public void setDocumentTypeMethod(String v){documentTypeMethod=v;}public String getInputFields(){return inputFields;}public void setInputFields(String v){inputFields=v;}
    public String getMaterialCitationRule(){return materialCitationRule;}public void setMaterialCitationRule(String v){materialCitationRule=v;}public String getAntiFabricationRule(){return antiFabricationRule;}public void setAntiFabricationRule(String v){antiFabricationRule=v;}
    public String getOutputFormat(){return outputFormat;}public void setOutputFormat(String v){outputFormat=v;}public String getRiskConstraint(){return riskConstraint;}public void setRiskConstraint(String v){riskConstraint=v;}
    public String getModelParameters(){return modelParameters;}public void setModelParameters(String v){modelParameters=v;}public Integer getCurrentVersion(){return currentVersion;}public void setCurrentVersion(Integer v){currentVersion=v;}
    public String getLifecycleStatus(){return lifecycleStatus;}public void setLifecycleStatus(String v){lifecycleStatus=v;}public String getTestSampleIds(){return testSampleIds;}public void setTestSampleIds(String v){testSampleIds=v;}
}
