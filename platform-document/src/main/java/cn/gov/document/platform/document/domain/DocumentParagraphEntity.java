package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_document_paragraph")
public class DocumentParagraphEntity extends BaseEntity {
    private String documentId;
    private Integer versionNumber;
    private String outlineNodeId;
    private Integer paragraphOrder;
    private String contentSourceLevel;
    private String sourceMaterialIds;
    private Boolean isLocked;
    private String contentOrigin;
    private String plainText;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String value) {
        documentId = value;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer value) {
        versionNumber = value;
    }

    public String getOutlineNodeId() {
        return outlineNodeId;
    }

    public void setOutlineNodeId(String value) {
        outlineNodeId = value;
    }

    public Integer getParagraphOrder() {
        return paragraphOrder;
    }

    public void setParagraphOrder(Integer value) {
        paragraphOrder = value;
    }

    /** §33 内容来源类型：AI生成、AI改写、AI推荐、人工录入、引用原文。 */
    public String getContentOrigin() {
        return contentOrigin;
    }

    public void setContentOrigin(String value) {
        contentOrigin = value;
    }

    public String getContentSourceLevel() {
        return contentSourceLevel;
    }

    public void setContentSourceLevel(String value) {
        contentSourceLevel = value;
    }

    public String getSourceMaterialIds() {
        return sourceMaterialIds;
    }

    public void setSourceMaterialIds(String value) {
        sourceMaterialIds = value;
    }

    public Boolean getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(Boolean value) {
        isLocked = value;
    }

    public String getPlainText() {
        return plainText;
    }

    public void setPlainText(String value) {
        plainText = value;
    }
}
