package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

/** 套红排版生成或在线编辑保存的定稿稿（DOCX 文件存放于对象存储）。 */
@TableName("ai_document_typeset")
public class DocumentTypesetEntity extends BaseEntity {
    private String documentId;
    private Integer documentVersion;
    private String templateId;
    private String templateName;
    private String fieldValues;
    private String deviations;
    private Integer deviationCount;
    private String filePath;
    private String fileHash;
    private Integer fileSize;
    private String source;
    private String parentId;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String v) {
        documentId = v;
    }

    /** 排版依据的正文版本；正文更新后定稿稿需要重新套红。 */
    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer v) {
        documentVersion = v;
    }

    /** 输出模板；为空表示使用内置标准版式。 */
    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String v) {
        templateId = v;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String v) {
        templateName = v;
    }

    /** 版头、版记等要素取值（JSON 对象）。 */
    public String getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(String v) {
        fieldValues = v;
    }

    /** L4 格式检查偏差（JSON 数组）。 */
    public String getDeviations() {
        return deviations;
    }

    public void setDeviations(String v) {
        deviations = v;
    }

    public Integer getDeviationCount() {
        return deviationCount;
    }

    public void setDeviationCount(Integer v) {
        deviationCount = v;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String v) {
        filePath = v;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String v) {
        fileHash = v;
    }

    public Integer getFileSize() {
        return fileSize;
    }

    public void setFileSize(Integer v) {
        fileSize = v;
    }

    /** 套红生成、在线编辑。 */
    public String getSource() {
        return source;
    }

    public void setSource(String v) {
        source = v;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String v) {
        parentId = v;
    }
}
