package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.DocumentDtos.DocumentResponse;
import cn.gov.document.platform.document.api.DocumentDtos.UpdateParagraphRequest;
import cn.gov.document.platform.document.api.DocumentSuggestionDtos;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.domain.DocumentSuggestionEntity;
import cn.gov.document.platform.document.persistence.DocumentSuggestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A7 修订模式：段落修改先作为修订建议提交，不直接形成新版本；接受时按与人工保存相同的规则形成新版本，拒绝则只记录处理结果。
 * 建议保存提出时的原文快照，接受前比对当前同一位置的段落，原文已被修改的建议视为过期，只能拒绝。
 */
@Service
public class DocumentSuggestionService {
    static final String PENDING = "待处理";
    static final String ACCEPTED = "已接受";
    static final String REJECTED = "已拒绝";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DocumentService documentService;
    private final DocumentSuggestionMapper suggestionMapper;

    public DocumentSuggestionService(DocumentService documentService, DocumentSuggestionMapper suggestionMapper) {
        this.documentService = documentService;
        this.suggestionMapper = suggestionMapper;
    }

    @Transactional
    public DocumentSuggestionDtos.SuggestionResponse create(
            String taskId, DocumentSuggestionDtos.CreateRequest request) {
        DocumentService.ProposedEdit proposed = documentService.proposeEdit(
                taskId,
                request.paragraphId(),
                new UpdateParagraphRequest(
                        request.plainText(),
                        request.lockVersion(),
                        null,
                        null,
                        request.richContent(),
                        request.adoptedGenerationIds()));
        DocumentParagraphEntity base = proposed.base();
        DocumentSuggestionEntity entity = new DocumentSuggestionEntity();
        entity.setTenantId(RequestContext.tenantId());
        entity.setDocumentId(proposed.document().getId());
        entity.setBaseParagraphId(base.getId());
        entity.setBaseVersion(base.getVersionNumber());
        entity.setOutlineNodeId(base.getOutlineNodeId());
        entity.setParagraphOrder(base.getParagraphOrder());
        entity.setBasePlainText(base.getPlainText());
        entity.setBaseRichContent(base.getRichContent());
        entity.setProposedPlainText(proposed.plainText());
        entity.setProposedRichContent(proposed.richContent());
        entity.setAdoptedGenerationIds(toJson(request.adoptedGenerationIds()));
        entity.setStatus(PENDING);
        entity.setProposerId(RequestContext.userId());
        suggestionMapper.insert(entity);
        return withCurrentParagraphs(proposed.document(), List.of(entity)).get(0);
    }

    public List<DocumentSuggestionDtos.SuggestionResponse> list(String taskId, String status) {
        DocumentEntity document = documentService.documentOf(taskId);
        return withCurrentParagraphs(
                document,
                suggestionMapper.selectList(new LambdaQueryWrapper<DocumentSuggestionEntity>()
                        .eq(DocumentSuggestionEntity::getDocumentId, document.getId())
                        .eq(status != null && !status.isBlank(), DocumentSuggestionEntity::getStatus, status)
                        .orderByAsc(DocumentSuggestionEntity::getCreatedAt)));
    }

    @Transactional
    public DocumentResponse accept(String taskId, String suggestionId, DocumentSuggestionDtos.DecisionRequest request) {
        DocumentSuggestionEntity suggestion = requirePending(taskId, suggestionId);
        DocumentResponse document = documentService.acceptEdit(
                taskId,
                baseSnapshot(suggestion),
                suggestion.getProposedPlainText(),
                suggestion.getProposedRichContent(),
                fromJson(suggestion.getAdoptedGenerationIds()),
                "接受 " + suggestion.getProposerId() + " 的修订");
        decide(suggestion, ACCEPTED, request);
        suggestion.setAcceptedVersion(document.currentVersion());
        suggestionMapper.updateById(suggestion);
        return document;
    }

    @Transactional
    public DocumentSuggestionDtos.SuggestionResponse reject(
            String taskId, String suggestionId, DocumentSuggestionDtos.DecisionRequest request) {
        DocumentSuggestionEntity suggestion = requirePending(taskId, suggestionId);
        decide(suggestion, REJECTED, request);
        suggestionMapper.updateById(suggestion);
        return withCurrentParagraphs(documentService.documentOf(taskId), List.of(suggestion))
                .get(0);
    }

    private DocumentSuggestionEntity requirePending(String taskId, String suggestionId) {
        DocumentEntity document = documentService.documentOf(taskId);
        DocumentSuggestionEntity suggestion =
                suggestionMapper.selectOne(new LambdaQueryWrapper<DocumentSuggestionEntity>()
                        .eq(DocumentSuggestionEntity::getId, suggestionId)
                        .eq(DocumentSuggestionEntity::getDocumentId, document.getId()));
        if (suggestion == null) throw BusinessException.notFound("修订不存在");
        if (!PENDING.equals(suggestion.getStatus()))
            throw BusinessException.conflict("该修订已处理：" + suggestion.getStatus());
        return suggestion;
    }

    private static void decide(
            DocumentSuggestionEntity suggestion, String status, DocumentSuggestionDtos.DecisionRequest request) {
        suggestion.setStatus(status);
        suggestion.setDecidedBy(RequestContext.userId());
        suggestion.setDecidedAt(OffsetDateTime.now());
        suggestion.setDecisionNote(
                request == null || request.note() == null || request.note().isBlank()
                        ? null
                        : request.note().strip());
    }

    /** 提出修订时的原文：只携带位置和内容，用于定位当前段落并判断是否已被修改。 */
    private static DocumentParagraphEntity baseSnapshot(DocumentSuggestionEntity suggestion) {
        DocumentParagraphEntity base = new DocumentParagraphEntity();
        base.setOutlineNodeId(suggestion.getOutlineNodeId());
        base.setParagraphOrder(suggestion.getParagraphOrder());
        base.setPlainText(suggestion.getBasePlainText());
        base.setRichContent(suggestion.getBaseRichContent());
        return base;
    }

    private List<DocumentSuggestionDtos.SuggestionResponse> withCurrentParagraphs(
            DocumentEntity document, List<DocumentSuggestionEntity> suggestions) {
        Map<String, DocumentParagraphEntity> current = documentService.currentParagraphs(document).stream()
                .collect(Collectors.toMap(
                        p -> position(p.getOutlineNodeId(), p.getParagraphOrder()), Function.identity(), (a, b) -> a));
        return suggestions.stream()
                .map(s -> {
                    DocumentParagraphEntity paragraph =
                            current.get(position(s.getOutlineNodeId(), s.getParagraphOrder()));
                    boolean stale = PENDING.equals(s.getStatus())
                            && (paragraph == null
                                    || !Objects.equals(paragraph.getPlainText(), s.getBasePlainText())
                                    || !Objects.equals(paragraph.getRichContent(), s.getBaseRichContent()));
                    return new DocumentSuggestionDtos.SuggestionResponse(
                            s.getId(),
                            s.getDocumentId(),
                            s.getBaseParagraphId(),
                            s.getBaseVersion(),
                            paragraph == null ? null : paragraph.getId(),
                            stale,
                            s.getStatus(),
                            s.getProposerId(),
                            s.getBasePlainText(),
                            s.getProposedPlainText(),
                            s.getProposedRichContent(),
                            !fromJson(s.getAdoptedGenerationIds()).isEmpty(),
                            s.getDecidedBy(),
                            s.getDecidedAt(),
                            s.getDecisionNote(),
                            s.getAcceptedVersion(),
                            s.getCreatedAt());
                })
                .toList();
    }

    private static String position(String outlineNodeId, Integer order) {
        return outlineNodeId + "#" + order;
    }

    private static String toJson(List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(ids.stream().distinct().toList());
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<String> fromJson(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return JSON.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException corrupted) {
            throw new IllegalStateException("修订采用的候选记录格式不正确", corrupted);
        }
    }
}
