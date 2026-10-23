package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.DocumentCommentDtos;
import cn.gov.document.platform.document.domain.DocumentCommentEntity;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.domain.DocumentVersionEntity;
import cn.gov.document.platform.document.persistence.DocumentCommentMapper;
import cn.gov.document.platform.document.persistence.DocumentMapper;
import cn.gov.document.platform.document.persistence.DocumentParagraphMapper;
import cn.gov.document.platform.document.persistence.DocumentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCommentService {
    private final DocumentMapper documentMapper;
    private final DocumentParagraphMapper paragraphMapper;
    private final DocumentVersionMapper versionMapper;
    private final DocumentCommentMapper commentMapper;

    public DocumentCommentService(
            DocumentMapper documentMapper,
            DocumentParagraphMapper paragraphMapper,
            DocumentVersionMapper versionMapper,
            DocumentCommentMapper commentMapper) {
        this.documentMapper = documentMapper;
        this.paragraphMapper = paragraphMapper;
        this.versionMapper = versionMapper;
        this.commentMapper = commentMapper;
    }

    @Transactional
    public DocumentCommentDtos.CommentResponse create(String documentId, DocumentCommentDtos.CreateRequest request) {
        DocumentEntity document = requireDocument(documentId);
        if (request.paragraphId() != null) {
            DocumentParagraphEntity paragraph =
                    paragraphMapper.selectOne(new LambdaQueryWrapper<DocumentParagraphEntity>()
                            .eq(DocumentParagraphEntity::getId, request.paragraphId())
                            .eq(DocumentParagraphEntity::getDocumentId, documentId));
            if (paragraph == null) throw BusinessException.notFound("批注段落不存在");
        }
        DocumentCommentEntity entity = new DocumentCommentEntity();
        entity.setTenantId(RequestContext.tenantId());
        entity.setDocumentId(documentId);
        entity.setParagraphId(request.paragraphId());
        entity.setCommenterId(RequestContext.userId());
        entity.setCommentType(request.commentType());
        entity.setCommentText(request.commentText().trim());
        entity.setQuotedText(
                request.quotedText() == null || request.quotedText().isBlank() ? null : request.quotedText().strip());
        entity.setIsResolved(false);
        commentMapper.insert(entity);
        return withCurrentParagraphs(document, List.of(entity)).get(0);
    }

    public List<DocumentCommentDtos.CommentResponse> list(String documentId, Boolean resolved) {
        DocumentEntity document = requireDocument(documentId);
        return withCurrentParagraphs(
                document,
                commentMapper.selectList(new LambdaQueryWrapper<DocumentCommentEntity>()
                        .eq(DocumentCommentEntity::getDocumentId, documentId)
                        .eq(resolved != null, DocumentCommentEntity::getIsResolved, resolved)
                        .orderByAsc(DocumentCommentEntity::getCreatedAt)));
    }

    /** 批注挂在创建时版本的段落上；按“大纲节点 + 段落序号”映射到当前版本同一位置的段落（与段落编辑的变基规则一致）。 */
    private List<DocumentCommentDtos.CommentResponse> withCurrentParagraphs(
            DocumentEntity document, List<DocumentCommentEntity> comments) {
        List<String> ids = comments.stream()
                .map(DocumentCommentEntity::getParagraphId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return comments.stream().map(c -> DocumentCommentDtos.CommentResponse.from(c, null)).toList();
        Map<String, DocumentParagraphEntity> origins =
                paragraphMapper.selectList(new LambdaQueryWrapper<DocumentParagraphEntity>()
                                .in(DocumentParagraphEntity::getId, ids)
                                .eq(DocumentParagraphEntity::getDocumentId, document.getId()))
                        .stream()
                        .collect(Collectors.toMap(DocumentParagraphEntity::getId, Function.identity()));
        Map<String, String> current = paragraphs(document.getId(), document.getCurrentVersion()).stream()
                .collect(Collectors.toMap(DocumentCommentService::position, DocumentParagraphEntity::getId, (a, b) -> a));
        return comments.stream()
                .map(comment -> {
                    DocumentParagraphEntity origin = origins.get(comment.getParagraphId());
                    return DocumentCommentDtos.CommentResponse.from(
                            comment, origin == null ? null : current.get(position(origin)));
                })
                .toList();
    }

    private static String position(DocumentParagraphEntity paragraph) {
        return paragraph.getOutlineNodeId() + "#" + paragraph.getParagraphOrder();
    }

    @Transactional
    public DocumentCommentDtos.CommentResponse resolve(String documentId, String commentId) {
        DocumentEntity document = requireDocument(documentId);
        DocumentCommentEntity entity = commentMapper.selectOne(new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getId, commentId)
                .eq(DocumentCommentEntity::getDocumentId, documentId));
        if (entity == null) throw BusinessException.notFound("批注不存在");
        if (!Boolean.TRUE.equals(entity.getIsResolved())) {
            entity.setIsResolved(true);
            entity.setResolvedBy(RequestContext.userId());
            entity.setResolvedAt(OffsetDateTime.now());
            commentMapper.updateById(entity);
        }
        return withCurrentParagraphs(document, List.of(entity)).get(0);
    }

    public DocumentCommentDtos.CompareResponse compare(String documentId, int fromVersion, int toVersion) {
        requireDocument(documentId);
        requireVersion(documentId, fromVersion);
        requireVersion(documentId, toVersion);
        Map<Integer, DocumentParagraphEntity> from = paragraphs(documentId, fromVersion).stream()
                .collect(Collectors.toMap(DocumentParagraphEntity::getParagraphOrder, Function.identity()));
        Map<Integer, DocumentParagraphEntity> to = paragraphs(documentId, toVersion).stream()
                .collect(Collectors.toMap(DocumentParagraphEntity::getParagraphOrder, Function.identity()));
        List<Integer> orders = new ArrayList<>(from.keySet());
        to.keySet().stream().filter(order -> !from.containsKey(order)).forEach(orders::add);
        orders.sort(Integer::compareTo);
        List<DocumentCommentDtos.ParagraphDiff> diffs = new ArrayList<>();
        int added = 0, deleted = 0, modified = 0, unchanged = 0;
        for (int order : orders) {
            DocumentParagraphEntity before = from.get(order), after = to.get(order);
            String status;
            if (before == null) {
                status = "新增";
                added++;
            } else if (after == null) {
                status = "删除";
                deleted++;
            } else if (java.util.Objects.equals(before.getPlainText(), after.getPlainText())) {
                status = "未变";
                unchanged++;
            } else {
                status = "修改";
                modified++;
            }
            diffs.add(new DocumentCommentDtos.ParagraphDiff(
                    order,
                    status,
                    before == null ? null : before.getId(),
                    after == null ? null : after.getId(),
                    before == null ? null : before.getPlainText(),
                    after == null ? null : after.getPlainText()));
        }
        return new DocumentCommentDtos.CompareResponse(
                documentId, fromVersion, toVersion, diffs, added, deleted, modified, unchanged);
    }

    private DocumentEntity requireDocument(String id) {
        DocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("文稿不存在");
        return entity;
    }

    private void requireVersion(String id, int version) {
        DocumentVersionEntity entity = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, id)
                .eq(DocumentVersionEntity::getVersionNumber, version));
        if (entity == null) throw BusinessException.notFound("文稿版本不存在");
        if (entity.getContentPurgedAt() != null)
            throw BusinessException.conflict("版本 " + version + " 正文已按版本保留策略清理，无法比较");
    }

    private List<DocumentParagraphEntity> paragraphs(String id, int version) {
        return paragraphMapper.selectList(new LambdaQueryWrapper<DocumentParagraphEntity>()
                .eq(DocumentParagraphEntity::getDocumentId, id)
                .eq(DocumentParagraphEntity::getVersionNumber, version)
                .orderByAsc(DocumentParagraphEntity::getParagraphOrder));
    }
}
