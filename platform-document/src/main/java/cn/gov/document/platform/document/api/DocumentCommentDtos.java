package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.domain.DocumentCommentEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentCommentDtos {
    private DocumentCommentDtos(){ }
    public record CreateRequest(String paragraphId,@NotBlank String commentType,@NotBlank @Size(max=10000) String commentText){ }
    public record CommentResponse(String id,String documentId,String paragraphId,String commenterId,String commentType,String commentText,
        boolean resolved,String resolvedBy,OffsetDateTime resolvedAt,int lockVersion,OffsetDateTime createdAt){
        public static CommentResponse from(DocumentCommentEntity e){return new CommentResponse(e.getId(),e.getDocumentId(),e.getParagraphId(),e.getCommenterId(),e.getCommentType(),e.getCommentText(),Boolean.TRUE.equals(e.getIsResolved()),e.getResolvedBy(),e.getResolvedAt(),e.getLockVersion(),e.getCreatedAt());}
    }
    public record ParagraphDiff(int paragraphOrder,String status,String fromParagraphId,String toParagraphId,String fromText,String toText){ }
    public record CompareResponse(String documentId,int fromVersion,int toVersion,List<ParagraphDiff> paragraphs,int added,int deleted,int modified,int unchanged){ }
}
