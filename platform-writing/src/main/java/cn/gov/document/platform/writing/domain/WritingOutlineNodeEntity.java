package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import cn.gov.document.platform.common.persistence.JsonClobTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(value = "ai_writing_outline_node", autoResultMap = true)
public class WritingOutlineNodeEntity extends BaseEntity {
    private String outlineId;
    private String parentNodeId;
    private Integer nodeOrder;
    private String nodeTitle;
    private String writingPurpose;
    private String keyFacts;

    @TableField(typeHandler = JsonClobTypeHandler.class)
    private String referencedMaterialIds;

    private Integer targetWordCount;
    private Boolean isRequired;
    private String generationStatus;
    private String humanNote;

    public String getOutlineId() {
        return outlineId;
    }

    public void setOutlineId(String value) {
        this.outlineId = value;
    }

    public String getParentNodeId() {
        return parentNodeId;
    }

    public void setParentNodeId(String value) {
        this.parentNodeId = value;
    }

    public Integer getNodeOrder() {
        return nodeOrder;
    }

    public void setNodeOrder(Integer value) {
        this.nodeOrder = value;
    }

    public String getNodeTitle() {
        return nodeTitle;
    }

    public void setNodeTitle(String value) {
        this.nodeTitle = value;
    }

    public String getWritingPurpose() {
        return writingPurpose;
    }

    public void setWritingPurpose(String value) {
        this.writingPurpose = value;
    }

    public String getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(String value) {
        this.keyFacts = value;
    }

    public String getReferencedMaterialIds() {
        return referencedMaterialIds;
    }

    public void setReferencedMaterialIds(String value) {
        this.referencedMaterialIds = value;
    }

    public Integer getTargetWordCount() {
        return targetWordCount;
    }

    public void setTargetWordCount(Integer value) {
        this.targetWordCount = value;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean value) {
        this.isRequired = value;
    }

    public String getGenerationStatus() {
        return generationStatus;
    }

    public void setGenerationStatus(String value) {
        this.generationStatus = value;
    }

    public String getHumanNote() {
        return humanNote;
    }

    public void setHumanNote(String value) {
        this.humanNote = value;
    }
}
