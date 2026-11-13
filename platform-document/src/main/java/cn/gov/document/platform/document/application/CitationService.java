package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.CitationDtos;
import cn.gov.document.platform.document.domain.DocumentCitationEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.persistence.DocumentCitationMapper;
import cn.gov.document.platform.knowledge.application.KnowledgeService;
import cn.gov.document.platform.writing.application.WritingMaterialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CitationService {
    private final DocumentCitationMapper mapper;
    private final DocumentService documentService;
    private final WritingMaterialService materialService;
    private final KnowledgeService knowledge;

    public CitationService(
            DocumentCitationMapper mapper,
            DocumentService documentService,
            WritingMaterialService materialService,
            KnowledgeService knowledge) {
        this.mapper = mapper;
        this.documentService = documentService;
        this.materialService = materialService;
        this.knowledge = knowledge;
    }

    @Transactional
    public CitationDtos.Response create(String taskId, CitationDtos.CreateRequest request) {
        DocumentService.ExportData data = documentService.exportData(taskId);
        DocumentParagraphEntity paragraph = data.paragraphs().stream()
                .filter(p -> p.getId().equals(request.paragraphId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("当前文稿版本中不存在该段落"));
        materialService.requireUsable(taskId, request.materialId());
        if (request.positionEnd() <= request.positionStart()
                || request.positionEnd() > paragraph.getPlainText().length())
            throw BusinessException.unprocessable("引用字符区间无效");
        String positioned = paragraph.getPlainText().substring(request.positionStart(), request.positionEnd());
        if ("原文摘录".equals(request.citationType()) && !positioned.equals(request.citedFragment()))
            throw BusinessException.unprocessable("原文摘录必须与段落定位内容一致");
        DocumentCitationEntity e = new DocumentCitationEntity();
        e.setTenantId(RequestContext.tenantId());
        e.setDocumentId(data.document().getId());
        e.setParagraphId(paragraph.getId());
        e.setMaterialId(request.materialId());
        e.setCitationType(request.citationType());
        e.setCitedFragment(request.citedFragment());
        e.setPositionStart(request.positionStart());
        e.setPositionEnd(request.positionEnd());
        e.setDisplayMode(StringUtils.hasText(request.displayMode()) ? request.displayMode() : "角标");
        mapper.insert(e);
        return CitationDtos.Response.from(e);
    }

    public List<CitationDtos.Response> list(String taskId) {
        DocumentService.ExportData data = documentService.exportData(taskId);
        if (data.paragraphs().isEmpty()) return List.of();
        return mapper
                .selectList(new LambdaQueryWrapper<DocumentCitationEntity>()
                        .eq(
                                DocumentCitationEntity::getDocumentId,
                                data.document().getId())
                        .in(
                                DocumentCitationEntity::getParagraphId,
                                data.paragraphs().stream()
                                        .map(DocumentParagraphEntity::getId)
                                        .toList())
                        .orderByAsc(DocumentCitationEntity::getParagraphId)
                        .orderByAsc(DocumentCitationEntity::getPositionStart))
                .stream()
                .map(citation -> {
                    CitationDtos.Response response = CitationDtos.Response.from(citation);
                    if (citation.getKnowledgeDocumentId() == null || !"已引用".equals(citation.getReviewStatus()))
                        return response;
                    DocumentParagraphEntity paragraph = data.paragraphs().stream()
                            .filter(p -> p.getId().equals(citation.getParagraphId()))
                            .findFirst()
                            .orElse(null);
                    boolean located = paragraph != null
                            && citation.getPositionStart() != null
                            && citation.getPositionEnd() != null
                            && citation.getPositionStart() >= 0
                            && citation.getPositionEnd()
                                    <= paragraph.getPlainText().length()
                            && citation.getPositionEnd() > citation.getPositionStart()
                            && paragraph
                                    .getPlainText()
                                    .substring(citation.getPositionStart(), citation.getPositionEnd())
                                    .equals(citation.getCitedFragment());
                    if (located
                            && knowledge.isCitationCurrent(
                                    citation.getKnowledgeDocumentId(),
                                    citation.getKnowledgeChunkId(),
                                    citation.getKnowledgeDocumentVersion(),
                                    citation.getSourceFileHash(),
                                    citation.getSourceExcerpt())) return response;
                    return new CitationDtos.Response(
                            response.id(),
                            response.documentId(),
                            response.paragraphId(),
                            response.materialId(),
                            response.citationType(),
                            response.citedFragment(),
                            response.positionStart(),
                            response.positionEnd(),
                            response.displayMode(),
                            response.knowledgeDocumentId(),
                            response.knowledgeChunkId(),
                            response.knowledgeDocumentVersion(),
                            response.sourceFileHash(),
                            response.sourceExcerpt(),
                            "待复核");
                })
                .toList();
    }

    @Transactional
    public CitationDtos.Response confirmKnowledge(
            String taskId, String citationId, CitationDtos.ConfirmKnowledgeRequest request) {
        if (!request.confirmed()) throw BusinessException.unprocessable("请先核对正文与本地原文后确认");
        DocumentService.ExportData data = documentService.exportData(taskId);
        DocumentCitationEntity citation = mapper.selectById(citationId);
        if (citation == null
                || !data.document().getId().equals(citation.getDocumentId())
                || citation.getKnowledgeDocumentId() == null) throw BusinessException.notFound("当前文稿的知识引用不存在");
        DocumentParagraphEntity paragraph = data.paragraphs().stream()
                .filter(p -> p.getId().equals(citation.getParagraphId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("引用段落已变化，请重新建立对应关系"));
        if (request.positionEnd() > paragraph.getPlainText().length()
                || request.positionEnd() <= request.positionStart()
                || !paragraph
                        .getPlainText()
                        .substring(request.positionStart(), request.positionEnd())
                        .equals(request.citedFragment())) throw BusinessException.unprocessable("正文定位范围与待确认文字不一致");
        var source = knowledge.readDocument(
                knowledgeSourceId(citation.getKnowledgeDocumentId()), citation.getKnowledgeDocumentId());
        if (source.document().documentVersion() != citation.getKnowledgeDocumentVersion()
                || !java.util.Objects.equals(source.document().originalFileHash(), citation.getSourceFileHash())
                || !"已核验".equals(source.document().verificationStatus())
                || "来源索引".equals(source.document().completeness())
                || "已失效".equals(source.document().validityStatus())
                || "已废止".equals(source.document().validityStatus())
                || "被替代".equals(source.document().validityStatus()))
            throw BusinessException.conflict("知识来源版本或核验状态已变化，请重新核对");
        boolean exactSource = source.chunks().stream()
                .anyMatch(chunk -> chunk.id().equals(citation.getKnowledgeChunkId())
                        && chunk.chunkText().contains(request.sourceExcerpt()));
        if (!exactSource) throw BusinessException.unprocessable("引用依据必须是原文切片中的连续文字");
        citation.setCitedFragment(request.citedFragment());
        citation.setPositionStart(request.positionStart());
        citation.setPositionEnd(request.positionEnd());
        citation.setSourceExcerpt(request.sourceExcerpt());
        citation.setReviewStatus("已引用");
        citation.setConfirmedBy(RequestContext.userId());
        citation.setConfirmedAt(java.time.OffsetDateTime.now());
        mapper.updateById(citation);
        return CitationDtos.Response.from(citation);
    }

    private String knowledgeSourceId(String documentId) {
        return knowledge.sourceIdForDocument(documentId);
    }

    @Transactional
    public void delete(String taskId, String citationId) {
        DocumentService.ExportData data = documentService.exportData(taskId);
        DocumentCitationEntity e = mapper.selectById(citationId);
        if (e == null || !data.document().getId().equals(e.getDocumentId())) throw BusinessException.notFound("引用不存在");
        mapper.deleteById(citationId);
    }
}
