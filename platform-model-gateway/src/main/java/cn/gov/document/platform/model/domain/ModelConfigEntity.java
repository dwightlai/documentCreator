package cn.gov.document.platform.model.domain;
import cn.gov.document.platform.common.domain.BaseEntity;import com.baomidou.mybatisplus.annotation.TableName;import com.baomidou.mybatisplus.annotation.TableField;import java.math.BigDecimal;
@TableName("ai_model_config") public class ModelConfigEntity extends BaseEntity {
    private String configCode,configName,provider,modelName,modelVersion,scene,dataLevel,endpointUrl,credentialRef;
    private Integer maxContextTokens,timeoutMs,maxRetries,qpsLimit,dailyTokenLimit;
    private BigDecimal costLimitDaily;@TableField("input_cost_per_1k") private BigDecimal inputCostPer1k;@TableField("output_cost_per_1k") private BigDecimal outputCostPer1k;private Boolean sensitiveWordFilter,outputValidation,isActive;
    public String getConfigCode(){return configCode;}public void setConfigCode(String v){configCode=v;}public String getConfigName(){return configName;}public void setConfigName(String v){configName=v;}
    public String getProvider(){return provider;}public void setProvider(String v){provider=v;}public String getModelName(){return modelName;}public void setModelName(String v){modelName=v;}public String getModelVersion(){return modelVersion;}public void setModelVersion(String v){modelVersion=v;}
    public String getScene(){return scene;}public void setScene(String v){scene=v;}public String getDataLevel(){return dataLevel;}public void setDataLevel(String v){dataLevel=v;}public Integer getMaxContextTokens(){return maxContextTokens;}public void setMaxContextTokens(Integer v){maxContextTokens=v;}
    public Integer getTimeoutMs(){return timeoutMs;}public void setTimeoutMs(Integer v){timeoutMs=v;}public Integer getMaxRetries(){return maxRetries;}public void setMaxRetries(Integer v){maxRetries=v;}public BigDecimal getCostLimitDaily(){return costLimitDaily;}public void setCostLimitDaily(BigDecimal v){costLimitDaily=v;}
    public Boolean getSensitiveWordFilter(){return sensitiveWordFilter;}public void setSensitiveWordFilter(Boolean v){sensitiveWordFilter=v;}public Boolean getOutputValidation(){return outputValidation;}public void setOutputValidation(Boolean v){outputValidation=v;}public Boolean getIsActive(){return isActive;}public void setIsActive(Boolean v){isActive=v;}
    public String getEndpointUrl(){return endpointUrl;}public void setEndpointUrl(String v){endpointUrl=v;}public String getCredentialRef(){return credentialRef;}public void setCredentialRef(String v){credentialRef=v;}
    public BigDecimal getInputCostPer1k(){return inputCostPer1k;}public void setInputCostPer1k(BigDecimal v){inputCostPer1k=v;}public BigDecimal getOutputCostPer1k(){return outputCostPer1k;}public void setOutputCostPer1k(BigDecimal v){outputCostPer1k=v;}
    public Integer getQpsLimit(){return qpsLimit;}public void setQpsLimit(Integer v){qpsLimit=v;}public Integer getDailyTokenLimit(){return dailyTokenLimit;}public void setDailyTokenLimit(Integer v){dailyTokenLimit=v;}
}
