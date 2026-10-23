package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.domain.DocumentCommentEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentCommentDtos {
    private DocumentCommentDtos() {}

    /** @param quotedText 批注针对的选中文字，为空表示针对整段 */
    public record CreateRequest(
            String paragraphId,
            @NotBlank @Size(max = 10) String commentType,
            @NotBlank @Size(max = 10000) String commentText,
            @Size(max = 1000) String quotedText) {
        public CreateRequest(String paragraphId, String commentType, String commentText) {
            this(paragraphId, commentType, commentText, null);
        }
    }

    /**
     * @param paragraphId 批注创建时所在版本的段落
     * @param currentParagraphId 当前版本中同一位置的段落；段落已被删除时为空。正文每次保存都会形成新版本段落，前端按此字段把批注挂到当前段落上
     */
    public record CommentResponse(
            String id,
            String documentId,
            String paragraphId,
            String commenterId,
            String commentType,
            String commentText,
            boolean resolved,
            String resolvedBy,
            OffsetDateTime resolvedAt,
            int lockVersion,
            OffsetDateTime createdAt,
            String quotedText,
            String currentParagraphId) {
        public static CommentResponse from(DocumentCommentEntity e, String currentParagraphId) {
            return new CommentResponse(
                    e.getId(),
                    e.getDocumentId(),
                    e.getParagraphId(),
                    e.getCommenterId(),
                    e.getCommentType(),
                    e.getCommentText(),
                    Boolean.TRUE.equals(e.getIsResolved()),
                    e.getResolvedBy(),
                    e.getResolvedAt(),
                    e.getLockVersion(),
                    e.getCreatedAt(),
                    e.getQuotedText(),
                    currentParagraphId);
        }
    }

    public record ParagraphDiff(
            int paragraphOrder,
            String status,
            String fromParagraphId,
            String toParagraphId,
            String fromText,
            String toText) {}

    public record CompareResponse(
            String documentId,
            int fromVersion,
            int toVersion,
            List<ParagraphDiff> paragraphs,
            int added,
            int deleted,
            int modified,
            int unchanged) {}
}
