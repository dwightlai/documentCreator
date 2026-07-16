package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_generation_record")
public class GenerationRecordEntity extends BaseEntity {
    private String taskId; private String documentId; private String paragraphId; private String generationType;
    private String promptTemplateId; private Integer promptTemplateVersion; private String modelConfigId; private String modelVersion;
    private Integer materialSnapshotVersion; private String inputContextHash; private String outputText; private String outputHash;
    private Integer durationMs; private Integer tokenCountInput; private Integer tokenCountOutput; private Boolean isStreaming; private Integer retryCount;
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getDocumentId(){return documentId;} public void setDocumentId(String v){documentId=v;}
    public String getParagraphId(){return paragraphId;} public void setParagraphId(String v){paragraphId=v;}
    public String getGenerationType(){return generationType;} public void setGenerationType(String v){generationType=v;}
    public String getPromptTemplateId(){return promptTemplateId;} public void setPromptTemplateId(String v){promptTemplateId=v;}
    public Integer getPromptTemplateVersion(){return promptTemplateVersion;} public void setPromptTemplateVersion(Integer v){promptTemplateVersion=v;}
    public String getModelConfigId(){return modelConfigId;} public void setModelConfigId(String v){modelConfigId=v;}
    public String getModelVersion(){return modelVersion;} public void setModelVersion(String v){modelVersion=v;}
    public Integer getMaterialSnapshotVersion(){return materialSnapshotVersion;} public void setMaterialSnapshotVersion(Integer v){materialSnapshotVersion=v;}
    public String getInputContextHash(){return inputContextHash;} public void setInputContextHash(String v){inputContextHash=v;}
    public String getOutputText(){return outputText;} public void setOutputText(String v){outputText=v;}
    public String getOutputHash(){return outputHash;} public void setOutputHash(String v){outputHash=v;}
    public Integer getDurationMs(){return durationMs;} public void setDurationMs(Integer v){durationMs=v;}
    public Integer getTokenCountInput(){return tokenCountInput;} public void setTokenCountInput(Integer v){tokenCountInput=v;}
    public Integer getTokenCountOutput(){return tokenCountOutput;} public void setTokenCountOutput(Integer v){tokenCountOutput=v;}
    public Boolean getIsStreaming(){return isStreaming;} public void setIsStreaming(Boolean v){isStreaming=v;}
    public Integer getRetryCount(){return retryCount;} public void setRetryCount(Integer v){retryCount=v;}
}
