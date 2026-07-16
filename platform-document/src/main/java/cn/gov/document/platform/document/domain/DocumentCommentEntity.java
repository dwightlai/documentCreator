package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_document_comment")
public class DocumentCommentEntity extends BaseEntity {
    private String documentId;private String paragraphId;private String commenterId;private String commentType;private String commentText;
    private Boolean isResolved;private String resolvedBy;private OffsetDateTime resolvedAt;
    public String getDocumentId(){return documentId;}public void setDocumentId(String v){documentId=v;}
    public String getParagraphId(){return paragraphId;}public void setParagraphId(String v){paragraphId=v;}
    public String getCommenterId(){return commenterId;}public void setCommenterId(String v){commenterId=v;}
    public String getCommentType(){return commentType;}public void setCommentType(String v){commentType=v;}
    public String getCommentText(){return commentText;}public void setCommentText(String v){commentText=v;}
    public Boolean getIsResolved(){return isResolved;}public void setIsResolved(Boolean v){isResolved=v;}
    public String getResolvedBy(){return resolvedBy;}public void setResolvedBy(String v){resolvedBy=v;}
    public OffsetDateTime getResolvedAt(){return resolvedAt;}public void setResolvedAt(OffsetDateTime v){resolvedAt=v;}
}
