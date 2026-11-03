package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.coordination.CoordinationStore;
import cn.gov.document.platform.common.coordination.ExclusiveGuard;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.model.ModelGenerationPort;
import cn.gov.document.platform.document.api.DocumentDtos.DocumentResponse;
import cn.gov.document.platform.document.api.DocumentDtos.EditLeaseResponse;
import cn.gov.document.platform.document.api.DocumentDtos.GenerateSectionRequest;
import cn.gov.document.platform.document.api.DocumentDtos.SelectionCandidate;
import cn.gov.document.platform.document.api.DocumentDtos.SelectionCandidatesRequest;
import cn.gov.document.platform.document.api.DocumentDtos.SelectionCandidatesResponse;
import cn.gov.document.platform.document.api.DocumentDtos.UpdateParagraphRequest;
import cn.gov.document.platform.document.api.DocumentDtos.VersionResponse;
import cn.gov.document.platform.document.domain.DocumentCitationEntity;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.domain.DocumentVersionEntity;
import cn.gov.document.platform.document.domain.GenerationRecordEntity;
import cn.gov.document.platform.document.persistence.DocumentCitationMapper;
import cn.gov.document.platform.document.persistence.DocumentMapper;
import cn.gov.document.platform.document.persistence.DocumentParagraphMapper;
import cn.gov.document.platform.document.persistence.DocumentVersionMapper;
import cn.gov.document.platform.document.persistence.GenerationRecordMapper;
import cn.gov.document.platform.writing.application.WritingMaterialService;
import cn.gov.document.platform.writing.application.WritingKnowledgeService;
import cn.gov.document.platform.writing.application.WritingQaService;
import cn.gov.document.platform.writing.application.WritingTaskService;
import cn.gov.document.platform.writing.domain.WritingOutlineEntity;
import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.domain.WritingQaEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import cn.gov.document.platform.writing.persistence.WritingOutlineMapper;
import cn.gov.document.platform.writing.persistence.WritingOutlineNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper versionMapper;
    private final DocumentParagraphMapper paragraphMapper;
    private final DocumentCitationMapper citationMapper;
    private final WritingOutlineMapper outlineMapper;
    private final WritingOutlineNodeMapper outlineNodeMapper;
    private final WritingTaskService taskService;
    private final RuleBasedDocumentGenerator generator;
    private final GenerationRecordMapper generationRecordMapper;
    private final WritingQaService qaService;
    private final WritingMaterialService materialService;
    private final WritingKnowledgeService writingKnowledgeService;
    private final cn.gov.document.platform.knowledge.application.KnowledgeService knowledgeService;
    private final ObjectProvider<ModelGenerationPort> modelPort;
    private final CoordinationStore coordination;
    private final ExclusiveGuard guard;

    /** 段落编辑租约有效期，客户端需在到期前续期（§34B.1）。 */
    public static final Duration EDIT_LEASE_TTL = Duration.ofMinutes(5);

    private static final Duration DOCUMENT_WRITE_TTL = Duration.ofMinutes(10);
    private static final Duration GENERATION_TTL = Duration.ofMinutes(10);

    public DocumentService(
            DocumentMapper documentMapper,
            DocumentVersionMapper versionMapper,
            DocumentParagraphMapper paragraphMapper,
            DocumentCitationMapper citationMapper,
            WritingOutlineMapper outlineMapper,
            WritingOutlineNodeMapper outlineNodeMapper,
            WritingTaskService taskService,
            RuleBasedDocumentGenerator generator,
            GenerationRecordMapper generationRecordMapper,
            WritingQaService qaService,
            WritingMaterialService materialService,
            WritingKnowledgeService writingKnowledgeService,
            cn.gov.document.platform.knowledge.application.KnowledgeService knowledgeService,
            ObjectProvider<ModelGenerationPort> modelPort,
            CoordinationStore coordination,
            ExclusiveGuard guard) {
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.paragraphMapper = paragraphMapper;
        this.citationMapper = citationMapper;
        this.outlineMapper = outlineMapper;
        this.outlineNodeMapper = outlineNodeMapper;
        this.taskService = taskService;
        this.generator = generator;
        this.generationRecordMapper = generationRecordMapper;
        this.qaService = qaService;
        this.materialService = materialService;
        this.writingKnowledgeService = writingKnowledgeService;
        this.knowledgeService = knowledgeService;
        this.modelPort = modelPort;
        this.coordination = coordination;
        this.guard = guard;
    }

    public DocumentResponse get(String taskId) {
        DocumentEntity document = requireDocument(taskId);
        return DocumentResponse.from(document, paragraphs(document.getId(), document.getCurrentVersion()));
    }

    @Transactional
    public DocumentResponse rebuild(String taskId, boolean confirmed) {
        return rebuild(taskId, confirmed, (percent, message) -> {});
    }

    @Transactional
    public DocumentResponse rebuild(String taskId, boolean confirmed, java.util.function.BiConsumer<Integer, String> progress) {
        if (!confirmed) throw BusinessException.unprocessable("重建正文前必须确认，原版本将保留在版本记录中");
        WritingTaskEntity task = taskService.require(taskId);
        if (task.getOutlineConfirmedVersion() == null) throw BusinessException.unprocessable("请先确认提纲");
        guard.holdUntilTransactionEnd("writing:" + taskId + ":generate", GENERATION_TTL, "该写作任务已有段落正在生成，请等待完成后再试");
        lockDocumentWrites(taskId);
        WritingOutlineEntity outline = outlineMapper.selectOne(new LambdaQueryWrapper<WritingOutlineEntity>()
                .eq(WritingOutlineEntity::getTaskId, taskId)
                .eq(WritingOutlineEntity::getOutlineVersion, task.getOutlineConfirmedVersion())
                .eq(WritingOutlineEntity::getIsConfirmed, true));
        if (outline == null) throw BusinessException.unprocessable("已确认提纲不存在，请重新确认");
        List<WritingOutlineNodeEntity> nodes = outlineNodeMapper.selectList(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                .eq(WritingOutlineNodeEntity::getOutlineId, outline.getId())
                .orderByAsc(WritingOutlineNodeEntity::getNodeOrder));
        if (nodes.isEmpty()) throw BusinessException.unprocessable("提纲没有章节");
        DocumentEntity document = findDocument(taskId);
        List<DocumentParagraphEntity> prior = document == null ? List.of() : paragraphs(document.getId(), document.getCurrentVersion());
        if (document == null) document = createDocument(task);
        else {
            document.setCurrentVersion(document.getCurrentVersion() + 1);
            createVersion(document, "AI生成", "人工确认", "按当前提纲重建正文");
        }
        int order = 1;
        for (WritingOutlineNodeEntity node : nodes) {
            progress.accept((order - 1) * 90 / nodes.size() + 5, "正在生成第 " + order + "/" + nodes.size() + " 节：" + node.getNodeTitle());
            long started = System.nanoTime();
            Generated generated = generateSectionText(task, node, regenerationContext(prior, node.getId(), null));
            DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
            paragraph.setTenantId(RequestContext.tenantId());
            paragraph.setDocumentId(document.getId());
            paragraph.setVersionNumber(document.getCurrentVersion());
            paragraph.setOutlineNodeId(node.getId());
            paragraph.setParagraphOrder(order++);
            paragraph.setContentSourceLevel(generated.sourceMaterialIds().isEmpty() ? "待核实" : "基于归纳");
            paragraph.setContentOrigin("AI生成");
            paragraph.setSourceMaterialIds(toJsonArray(generated.sourceMaterialIds()));
            paragraph.setSourceKnowledgeIds(toJsonArray(writingKnowledgeService.selections(taskId, node.getId()).stream()
                    .map(cn.gov.document.platform.writing.domain.WritingKnowledgeSelectionEntity::getId).toList()));
            paragraph.setIsLocked(false);
            paragraph.setPlainText(generated.output());
            paragraphMapper.insert(paragraph);
            linkKnowledgeCandidates(taskId, node.getId(), document.getId(), paragraph);
            node.setGenerationStatus("已生成");
            outlineNodeMapper.updateById(node);
            recordGeneration(taskId, document.getId(), paragraph.getId(), "章节生成", null, generated, started, false);
        }
        refreshDocument(document);
        taskService.attachCurrentDocument(taskId, document.getId());
        return get(taskId);
    }

    @Transactional
    public cn.gov.document.platform.writing.api.WritingTaskDtos.Response rename(
            String taskId, cn.gov.document.platform.writing.api.WritingTaskDtos.RenameRequest request) {
        var task = taskService.rename(taskId, request.title(), request.lockVersion());
        DocumentEntity document = findDocument(taskId);
        if (document != null) {
            document.setTitle(task.taskName());
            documentMapper.updateById(document);
        }
        return task;
    }

    /** 审校页修改标题时，标题和正文一起形成可追溯的新版本。 */
    @Transactional
    public DocumentResponse renameForReview(String taskId, String title) {
        lockDocumentWrites(taskId);
        DocumentEntity document = requireDocument(taskId);
        var writingTask = taskService.require(taskId);
        if (title == null || title.isBlank() || title.length() > 200)
            throw BusinessException.unprocessable("标题不能为空，且不能超过 200 字");
        if (title.equals(document.getTitle())) return get(taskId);
        taskService.rename(taskId, title, writingTask.getLockVersion());
        int nextVersion = document.getCurrentVersion() + 1;
        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        document.setCurrentVersion(nextVersion);
        document.setTitle(title);
        current.forEach(item -> insertParagraphClone(item, nextVersion));
        createVersion(document, "人工提交", "用户手工", "审校中修改标题");
        refreshDocument(document);
        return get(taskId);
    }

    public List<VersionResponse> versions(String taskId) {
        DocumentEntity document = requireDocument(taskId);
        return versionMapper
                .selectList(new LambdaQueryWrapper<DocumentVersionEntity>()
                        .eq(DocumentVersionEntity::getDocumentId, document.getId())
                        .orderByDesc(DocumentVersionEntity::getVersionNumber))
                .stream()
                .map(VersionResponse::from)
                .toList();
    }

    public DocumentResponse version(String taskId, int versionNumber) {
        DocumentEntity document = requireDocument(taskId);
        requireVersion(document.getId(), versionNumber);
        DocumentEntity snapshot = copyMetadata(document);
        snapshot.setCurrentVersion(versionNumber);
        return DocumentResponse.from(snapshot, paragraphs(document.getId(), versionNumber));
    }

    @Transactional
    public DocumentResponse generateSection(String taskId, GenerateSectionRequest request) {
        return generateSectionInternal(taskId, request, false);
    }

    @Transactional
    public DocumentResponse generateSectionStreaming(String taskId, GenerateSectionRequest request) {
        return generateSectionInternal(taskId, request, true);
    }

    private DocumentResponse generateSectionInternal(String taskId, GenerateSectionRequest request, boolean streaming) {
        long started = System.nanoTime();
        WritingTaskEntity task = taskService.require(taskId);
        guard.holdUntilTransactionEnd("writing:" + taskId + ":generate", GENERATION_TTL, "该写作任务已有段落正在生成，请等待完成后再试");
        lockDocumentWrites(taskId);
        if (task.getOutlineConfirmedVersion() == null) {
            throw BusinessException.unprocessable("生成正文前必须确认大纲");
        }
        WritingOutlineEntity outline = outlineMapper.selectOne(new LambdaQueryWrapper<WritingOutlineEntity>()
                .eq(WritingOutlineEntity::getTaskId, taskId)
                .eq(WritingOutlineEntity::getOutlineVersion, task.getOutlineConfirmedVersion())
                .eq(WritingOutlineEntity::getIsConfirmed, true));
        if (outline == null) throw BusinessException.unprocessable("已确认大纲不存在，请重新确认");
        WritingOutlineNodeEntity node = outlineNodeMapper.selectOne(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                .eq(WritingOutlineNodeEntity::getId, request.outlineNodeId())
                .eq(WritingOutlineNodeEntity::getOutlineId, outline.getId()));
        if (node == null) throw BusinessException.notFound("大纲节点不存在");

        DocumentEntity document = findDocument(taskId);
        if (document == null) document = createDocument(task);
        else beginVersion(document, "AI生成", "AI生成", "生成章节：" + node.getNodeTitle());

        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        List<DocumentParagraphEntity> previousSection = current.stream()
                .filter(item -> node.getId().equals(item.getOutlineNodeId()))
                .toList();
        if (previousSection.stream().anyMatch(item -> Boolean.TRUE.equals(item.getIsLocked())))
            throw BusinessException.conflict("本节有锁定段落，请先解锁后再重新生成");
        int order = previousSection.stream().mapToInt(DocumentParagraphEntity::getParagraphOrder).min().orElseGet(() -> current.stream()
                        .mapToInt(DocumentParagraphEntity::getParagraphOrder)
                        .max()
                        .orElse(0)
                + 1);
        previousSection.forEach(item -> paragraphMapper.deleteById(item.getId()));
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setTenantId(RequestContext.tenantId());
        paragraph.setDocumentId(document.getId());
        paragraph.setVersionNumber(document.getCurrentVersion());
        paragraph.setOutlineNodeId(node.getId());
        paragraph.setParagraphOrder(order);
        paragraph.setContentSourceLevel("基于归纳");
        paragraph.setContentOrigin("AI生成");
        paragraph.setSourceMaterialIds(
                node.getReferencedMaterialIds() == null ? "[]" : node.getReferencedMaterialIds());
        paragraph.setIsLocked(false);
        Generated generated = generateSectionText(task, node, regenerationContext(previousSection, node.getId(), request.instruction()));
        paragraph.setSourceMaterialIds(toJsonArray(generated.sourceMaterialIds()));
        paragraph.setSourceKnowledgeIds(toJsonArray(writingKnowledgeService.selections(taskId, node.getId()).stream()
                .map(cn.gov.document.platform.writing.domain.WritingKnowledgeSelectionEntity::getId).toList()));
        paragraph.setPlainText(generated.output());
        paragraphMapper.insert(paragraph);
        linkKnowledgeCandidates(taskId, node.getId(), document.getId(), paragraph);
        node.setGenerationStatus("已生成");
        outlineNodeMapper.updateById(node);
        refreshDocument(document);
        taskService.attachCurrentDocument(taskId, document.getId());
        recordGeneration(
                taskId,
                document.getId(),
                paragraph.getId(),
                "章节生成",
                request.instruction(),
                generated,
                started,
                streaming);
        return get(taskId);
    }

    @Transactional
    public DocumentResponse transform(
            String taskId,
            String generationType,
            cn.gov.document.platform.document.api.DocumentDtos.TransformRequest request) {
        long started = System.nanoTime();
        WritingTaskEntity task = taskService.require(taskId);
        guard.holdUntilTransactionEnd("writing:" + taskId + ":generate", GENERATION_TTL, "该写作任务已有段落正在生成，请等待完成后再试");
        lockDocumentWrites(taskId);
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity source = requireCurrentParagraph(document, request.paragraphId());
        if (Boolean.TRUE.equals(source.getIsLocked())) throw BusinessException.conflict("段落已锁定，不能执行 AI 写作操作");
        ensureNotLeasedByOthers(document, source);
        if (!source.getLockVersion().equals(request.lockVersion()))
            throw BusinessException.conflict("段落已被其他用户修改，请刷新后重试");
        if (!List.of("续写", "改写", "压缩", "扩写").contains(generationType))
            throw BusinessException.unprocessable("不支持的生成类型");
        Generated generated = transformText(
                generationType, task, source.getPlainText(), request.instruction(), request.targetWordCount());
        String output = generated.output();
        int nextVersion = document.getCurrentVersion() + 1;
        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        document.setCurrentVersion(nextVersion);
        String outputParagraphId = null;
        for (DocumentParagraphEntity old : current) {
            DocumentParagraphEntity copy = cloneParagraph(old, nextVersion);
            if ("续写".equals(generationType) && old.getParagraphOrder() > source.getParagraphOrder())
                copy.setParagraphOrder(old.getParagraphOrder() + 1);
            if (!"续写".equals(generationType) && old.getId().equals(source.getId())) {
                copy.setPlainText(output);
                copy.setRichContent(null);
                copy.setContentSourceLevel("无来源");
                copy.setContentOrigin("AI改写");
            }
            paragraphMapper.insert(copy);
            if ("续写".equals(generationType) || !old.getId().equals(source.getId()))
                cloneCitations(old.getId(), copy.getId(), document.getId());
            if (!"续写".equals(generationType) && old.getId().equals(source.getId())) outputParagraphId = copy.getId();
        }
        if ("续写".equals(generationType)) {
            DocumentParagraphEntity continuation = new DocumentParagraphEntity();
            continuation.setTenantId(RequestContext.tenantId());
            continuation.setDocumentId(document.getId());
            continuation.setVersionNumber(nextVersion);
            continuation.setOutlineNodeId(source.getOutlineNodeId());
            continuation.setParagraphOrder(source.getParagraphOrder() + 1);
            continuation.setContentSourceLevel("无来源");
            continuation.setContentOrigin("AI生成");
            continuation.setSourceMaterialIds("[]");
            continuation.setIsLocked(false);
            continuation.setPlainText(output);
            paragraphMapper.insert(continuation);
            outputParagraphId = continuation.getId();
        }
        createVersion(document, "AI生成", "AI改写", generationType + "段落");
        refreshDocument(document);
        recordGeneration(
                taskId,
                document.getId(),
                outputParagraphId,
                generationType,
                source.getPlainText() + "\n" + request.instruction(),
                generated,
                started,
                false);
        return get(taskId);
    }

    @Transactional
    public DocumentResponse insertQaAnswer(
            String taskId, String qaId, cn.gov.document.platform.writing.api.WritingQaDtos.InsertRequest request) {
        taskService.require(taskId);
        throw BusinessException.unprocessable("问答摘录汇总不能直接插入正文；请在正文工作区选用原文并预览后插入");
    }

    public cn.gov.document.platform.document.api.DocumentDtos.KnowledgeDraftResponse previewKnowledgeDraft(
            String taskId, cn.gov.document.platform.document.api.DocumentDtos.KnowledgeDraftRequest request) {
        var selection = writingKnowledgeService.requireSelection(taskId, request.selectionId());
        WritingTaskEntity task = taskService.require(taskId);
        ModelGenerationPort port = modelPort.getIfAvailable();
        if (port == null) throw BusinessException.unprocessable("没有可用的正文生成模型");
        String prompt = "请根据已选用资料起草一段可插入公文的表述。只输出正文，不输出资料列表、引用标记或解释。"
                + "写法参考仅用于结构与语气，不得复制其中的具体事实。未能核实的事实不得补造。\n"
                + "文种：" + task.getDocumentTypeCode() + "\n主题：" + task.getWritingTopic()
                + "\n用途：" + selection.getUsageType() + "\n来源：" + selection.getDocumentTitle()
                + "\n选用资料：" + ("写法参考".equals(selection.getUsageType())
                        ? cn.gov.document.platform.writing.application.WritingKnowledgeService.styleGuidance(selection.getExcerpt())
                        : selection.getExcerpt())
                + "\n补充要求：" + (request.instruction() == null ? "" : request.instruction());
        var generated = port.generate("段落生成", dataLevel(task.getSecurityLevel()), task.getDocumentTypeCode(),
                prompt, Map.of("topic", task.getWritingTopic(), "section", "依据起草", "instruction",
                        request.instruction() == null ? "" : request.instruction(), "materials",
                        "写法参考".equals(selection.getUsageType())
                                ? cn.gov.document.platform.writing.application.WritingKnowledgeService.styleGuidance(selection.getExcerpt())
                                : selection.getExcerpt()),
                "写作", taskId);
        if (generated.isEmpty() || generated.get().output().isBlank())
            throw BusinessException.unprocessable("模型未生成可用草稿，请先配置写作模型");
        return new cn.gov.document.platform.document.api.DocumentDtos.KnowledgeDraftResponse(
                generated.get().output().trim(), selection.getId(), "待核稿");
    }

    @Transactional
    public DocumentResponse insertKnowledge(String taskId,
            cn.gov.document.platform.document.api.DocumentDtos.KnowledgeInsertionRequest request) {
        if (!request.confirmed()) throw BusinessException.unprocessable("请先预览并确认插入内容");
        var selected = writingKnowledgeService.requireSelection(taskId, request.selectionId());
        boolean original = "原文摘录".equals(request.mode());
        if (original) writingKnowledgeService.requireOriginalSelection(taskId, request.selectionId());
        if (!original && !"起草表述".equals(request.mode()))
            throw BusinessException.unprocessable("插入方式只能是原文摘录或起草表述");
        String text = request.text().trim();
        if (original && (!selected.getExcerpt().contains(text) || "写法参考".equals(selected.getUsageType())))
            throw BusinessException.unprocessable("原文摘录必须是所选原文中的连续文字，写法参考不能当作事实引用");
        lockDocumentWrites(taskId);
        DocumentEntity document = findDocument(taskId);
        boolean created = document == null;
        if (created) document = createDocument(taskService.require(taskId));
        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        DocumentParagraphEntity anchor = request.afterParagraphId() == null || request.afterParagraphId().isBlank()
                ? current.stream().filter(p -> selected.getOutlineNodeId().equals(p.getOutlineNodeId()))
                        .reduce((first, second) -> second).orElse(null)
                : requireCurrentParagraph(document, request.afterParagraphId());
        if (anchor != null && !selected.getOutlineNodeId().equals(anchor.getOutlineNodeId()))
            throw BusinessException.unprocessable("插入位置必须属于已选用依据的章节");
        int order = anchor == null ? current.stream().mapToInt(DocumentParagraphEntity::getParagraphOrder).max().orElse(0)
                : anchor.getParagraphOrder();
        int next = created ? document.getCurrentVersion() : document.getCurrentVersion() + 1;
        for (DocumentParagraphEntity old : current) {
            DocumentParagraphEntity copy = cloneParagraph(old, next);
            if (old.getParagraphOrder() > order) copy.setParagraphOrder(old.getParagraphOrder() + 1);
            paragraphMapper.insert(copy);
            cloneCitations(old.getId(), copy.getId(), document.getId());
        }
        document.setCurrentVersion(next);
        DocumentParagraphEntity inserted = new DocumentParagraphEntity();
        inserted.setTenantId(RequestContext.tenantId());
        inserted.setDocumentId(document.getId());
        inserted.setVersionNumber(next);
        inserted.setOutlineNodeId(selected.getOutlineNodeId());
        inserted.setParagraphOrder(order + 1);
        inserted.setContentSourceLevel(original ? "有直接来源" : "待核稿");
        inserted.setContentOrigin(original ? "引用原文" : "AI推荐");
        inserted.setSourceMaterialIds("[]");
        inserted.setSourceKnowledgeIds(toJsonArray(List.of(selected.getId())));
        inserted.setIsLocked(false);
        inserted.setPlainText(text);
        paragraphMapper.insert(inserted);
        DocumentCitationEntity citation = new DocumentCitationEntity();
        citation.setTenantId(RequestContext.tenantId());
        citation.setDocumentId(document.getId());
        citation.setParagraphId(inserted.getId());
        citation.setKnowledgeDocumentId(selected.getKnowledgeDocumentId());
        citation.setKnowledgeChunkId(selected.getChunkId());
        citation.setKnowledgeDocumentVersion(selected.getDocumentVersion());
        citation.setSourceFileHash(selected.getFileHash());
        citation.setSourceExcerpt(original ? text : selected.getExcerpt());
        citation.setCitationType(original ? "原文摘录" : "候选依据");
        citation.setCitedFragment(text);
        citation.setPositionStart(0);
        citation.setPositionEnd(text.length());
        citation.setDisplayMode("内部留痕");
        citation.setReviewStatus(original ? "已引用" : "待复核");
        if (original) { citation.setConfirmedBy(RequestContext.userId()); citation.setConfirmedAt(java.time.OffsetDateTime.now()); }
        citationMapper.insert(citation);
        if (!created) createVersion(document, "人工提交", "用户确认插入", original ? "插入知识原文摘录" : "插入依据起草表述");
        refreshDocument(document);
        return get(taskId);
    }

    public List<cn.gov.document.platform.document.api.DocumentDtos.GenerationRecordResponse> generationRecords(
            String taskId) {
        taskService.require(taskId);
        return generationRecordMapper
                .selectList(new LambdaQueryWrapper<GenerationRecordEntity>()
                        .eq(GenerationRecordEntity::getTaskId, taskId)
                        .orderByDesc(GenerationRecordEntity::getCreatedAt))
                .stream()
                .map(cn.gov.document.platform.document.api.DocumentDtos.GenerationRecordResponse::from)
                .toList();
    }

    @Transactional
    public DocumentResponse updateParagraph(String taskId, String paragraphId, UpdateParagraphRequest request) {
        lockDocumentWrites(taskId);
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity source = resolveEditableParagraph(document, paragraphId, request.lockVersion());
        if (Boolean.TRUE.equals(source.getIsLocked())) throw BusinessException.conflict("段落已锁定，不能修改");
        ensureNotLeasedByOthers(document, source);
        EditedContent edited = editedContent(source, request);
        boolean aiAssisted = adoptsSelectionCandidates(document, source, request.adoptedGenerationIds());
        applyEdit(document, source, edited, aiAssisted, "用户手工", request.changeDescription());
        return get(taskId);
    }

    @Transactional
    public DocumentResponse deleteParagraph(String taskId, String paragraphId) {
        lockDocumentWrites(taskId);
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity target = requireCurrentParagraph(document, paragraphId);
        if (Boolean.TRUE.equals(target.getIsLocked())) throw BusinessException.conflict("请先解锁段落再删除");
        ensureNotLeasedByOthers(document, target);
        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        int next = document.getCurrentVersion() + 1;
        for (DocumentParagraphEntity old : current) {
            if (old.getId().equals(paragraphId)) continue;
            DocumentParagraphEntity copy = cloneParagraph(old, next);
            paragraphMapper.insert(copy);
            cloneCitations(old.getId(), copy.getId(), document.getId());
        }
        document.setCurrentVersion(next);
        createVersion(document, "人工提交", "用户手工", "删除第 " + target.getParagraphOrder() + " 段");
        refreshDocument(document);
        return get(taskId);
    }

    /** 以修改后的段落形成新版本；人工保存和接受修订共用，保证来源标识、引用复制和版本记录规则一致。 */
    private void applyEdit(
            DocumentEntity document,
            DocumentParagraphEntity source,
            EditedContent edited,
            boolean aiAssisted,
            String changeSource,
            String description) {
        int nextVersion = document.getCurrentVersion() + 1;
        List<DocumentParagraphEntity> oldParagraphs = paragraphs(document.getId(), document.getCurrentVersion());
        document.setCurrentVersion(nextVersion);
        for (DocumentParagraphEntity old : oldParagraphs) {
            DocumentParagraphEntity copy = cloneParagraph(old, nextVersion);
            boolean edit = old.getId().equals(source.getId());
            if (edit) {
                copy.setPlainText(edited.plainText());
                copy.setRichContent(edited.richContent());
                copy.setContentSourceLevel(aiAssisted ? "无来源" : "手工");
                copy.setContentOrigin(aiAssisted ? "AI改写" : "人工录入");
            }
            paragraphMapper.insert(copy);
            if (edit) cloneCitations(old.getId(), copy.getId(), document.getId(), true);
            else cloneCitations(old.getId(), copy.getId(), document.getId());
        }
        createVersion(document, "人工提交", changeSource, description);
        refreshDocument(document);
    }

    /** 修订模式下提出的段落修改：base 为提出时的原文段落，内容已按与直接保存相同的规则校验和推导。 */
    record ProposedEdit(DocumentEntity document, DocumentParagraphEntity base, String plainText, String richContent) {}

    /** 修订建议使用：按与直接保存相同的规则校验并推导修改内容，不修改正文。 */
    ProposedEdit proposeEdit(String taskId, String paragraphId, UpdateParagraphRequest request) {
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity base = resolveEditableParagraph(document, paragraphId, request.lockVersion());
        if (Boolean.TRUE.equals(base.getIsLocked())) throw BusinessException.conflict("段落已锁定，不能提出修订");
        EditedContent edited = editedContent(base, request);
        if (edited.plainText().equals(base.getPlainText())
                && Objects.equals(edited.richContent(), base.getRichContent()))
            throw BusinessException.unprocessable("修订内容与原文相同");
        adoptsSelectionCandidates(document, base, request.adoptedGenerationIds());
        return new ProposedEdit(document, base, edited.plainText(), edited.richContent());
    }

    /**
     * 接受修订：当前版本同一位置的段落必须仍是提出修订时的原文，否则说明段落已被他人修改，返回冲突而不是覆盖。
     *
     * @param base 提出修订时的原文快照（位置和内容）
     */
    DocumentResponse acceptEdit(
            String taskId,
            DocumentParagraphEntity base,
            String plainText,
            String richContent,
            List<String> adoptedGenerationIds,
            String description) {
        lockDocumentWrites(taskId);
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity current = paragraphs(document.getId(), document.getCurrentVersion()).stream()
                .filter(p -> samePosition(p, base))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("修订针对的段落已被删除或移动"));
        if (!Objects.equals(current.getPlainText(), base.getPlainText())
                || !Objects.equals(current.getRichContent(), base.getRichContent()))
            throw BusinessException.conflict("段落在提出修订后已被修改，该修订已过期");
        if (Boolean.TRUE.equals(current.getIsLocked())) throw BusinessException.conflict("段落已锁定，不能接受修订");
        ensureNotLeasedByOthers(document, current);
        boolean aiAssisted = adoptsSelectionCandidates(document, current, adoptedGenerationIds);
        applyEdit(document, current, new EditedContent(plainText, richContent), aiAssisted, "修订接受", description);
        return get(taskId);
    }

    DocumentEntity documentOf(String taskId) {
        return requireDocument(taskId);
    }

    List<DocumentParagraphEntity> currentParagraphs(DocumentEntity document) {
        return paragraphs(document.getId(), document.getCurrentVersion());
    }

    private record EditedContent(String plainText, String richContent) {}

    /**
     * 富文本编辑提交时以富文本为准推导纯文本，保证审校看到的就是导出的内容；只提交纯文本时（如采纳审校建议）尽量把改动落回原富文本以保留格式，
     * 无法安全落回时降级为纯文本段落。
     */
    private static EditedContent editedContent(DocumentParagraphEntity source, UpdateParagraphRequest request) {
        if (request.richContent() != null && !request.richContent().isBlank()) {
            String text = RichContent.plainText(RichContent.parse(request.richContent()));
            if (text.isEmpty()) throw BusinessException.unprocessable("段落内容不能为空");
            if (text.length() > 30000) throw BusinessException.unprocessable("段落内容不能超过 30000 字");
            return new EditedContent(text, request.richContent());
        }
        String text = request.plainText().trim();
        String rich = source.getRichContent() == null
                ? null
                : RichContent.rebase(source.getRichContent(), source.getPlainText(), text);
        return new EditedContent(text, rich);
    }

    /** 划词操作对应的生成类型（生成记录 generation_type）；采用候选时据此确认记录来自划词改写。 */
    private static final Map<String, String> SELECTION_TYPES =
            Map.of("改写", "划词改写", "润色", "划词润色", "扩写", "划词扩写", "缩写", "划词缩写");

    /** 多个候选之间的差异来自明确的表达风格，而不是随机采样，便于用户理解为什么给出这几个版本。 */
    private static final List<String> CANDIDATE_STYLES = List.of("规范庄重", "简洁凝练", "具体充实");

    /** 为选中文字生成候选表达，不修改正文；每个候选单独留存生成记录，供采用时核验和 AI 标识统计。 */
    @Transactional
    public SelectionCandidatesResponse selectionCandidates(String taskId, SelectionCandidatesRequest request) {
        WritingTaskEntity task = taskService.require(taskId);
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity paragraph = currentParagraphAt(document, request.paragraphId());
        if (Boolean.TRUE.equals(paragraph.getIsLocked())) throw BusinessException.conflict("段落已锁定，不能执行 AI 写作操作");
        ensureNotLeasedByOthers(document, paragraph);
        String type = SELECTION_TYPES.get(request.operation());
        String selected = request.selectedText().strip();
        int count = request.count() == null ? CANDIDATE_STYLES.size() : request.count();
        java.util.Set<String> seen = new java.util.HashSet<>(List.of(selected));
        List<SelectionCandidate> candidates = new ArrayList<>();
        for (String style : CANDIDATE_STYLES.subList(0, count)) {
            long started = System.nanoTime();
            Generated generated = selectionText(
                    request.operation(), task, paragraph.getPlainText(), selected, request.instruction(), style);
            String output = generated.output() == null ? "" : generated.output().strip();
            // 规则引擎兜底时不同风格可能给出相同结果，与原文相同或重复的候选不返回
            if (output.isEmpty() || !seen.add(output)) continue;
            Generated stripped = generated.withOutput(output);
            String id =
                    recordGeneration(taskId, document.getId(), paragraph.getId(), type, null, stripped, started, false);
            candidates.add(new SelectionCandidate(id, style, output, generated.modelVersion()));
        }
        return new SelectionCandidatesResponse(paragraph.getId(), request.operation(), selected, candidates);
    }

    /** 保存时核验采用的候选确实是为该段落（同一位置）生成的划词候选；采用任一候选的段落整体标记为 AI 改写。 */
    private boolean adoptsSelectionCandidates(
            DocumentEntity document, DocumentParagraphEntity paragraph, List<String> generationIds) {
        if (generationIds == null || generationIds.isEmpty()) return false;
        java.util.Set<String> ids = new java.util.HashSet<>(generationIds);
        List<GenerationRecordEntity> records =
                generationRecordMapper.selectList(new LambdaQueryWrapper<GenerationRecordEntity>()
                        .in(GenerationRecordEntity::getId, ids)
                        .eq(GenerationRecordEntity::getDocumentId, document.getId()));
        if (records.size() != ids.size()) throw BusinessException.unprocessable("采用的 AI 候选不存在");
        for (GenerationRecordEntity record : records) {
            if (!SELECTION_TYPES.containsValue(record.getGenerationType()))
                throw BusinessException.unprocessable("采用的记录不是划词改写候选");
            DocumentParagraphEntity origin = paragraphMapper.selectOne(new LambdaQueryWrapper<DocumentParagraphEntity>()
                    .eq(DocumentParagraphEntity::getId, record.getParagraphId())
                    .eq(DocumentParagraphEntity::getDocumentId, document.getId()));
            if (origin == null || !samePosition(origin, paragraph))
                throw BusinessException.unprocessable("采用的 AI 候选不属于该段落");
        }
        return true;
    }

    @Transactional
    public DocumentResponse setLocked(String taskId, String paragraphId, boolean locked) {
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity paragraph = requireCurrentParagraph(document, paragraphId);
        paragraph.setIsLocked(locked);
        paragraphMapper.updateById(paragraph);
        return get(taskId);
    }

    @Transactional
    public DocumentResponse restore(String taskId, int versionNumber) {
        lockDocumentWrites(taskId);
        DocumentEntity document = requireDocument(taskId);
        requireVersion(document.getId(), versionNumber);
        List<DocumentParagraphEntity> restored = paragraphs(document.getId(), versionNumber);
        int nextVersion = document.getCurrentVersion() + 1;
        document.setCurrentVersion(nextVersion);
        restored.forEach(item -> insertParagraphClone(item, nextVersion));
        createVersion(document, "人工提交", "版本恢复", "从版本 " + versionNumber + " 恢复");
        refreshDocument(document);
        return get(taskId);
    }

    public ExportData exportData(String taskId) {
        return exportData(taskId, false);
    }

    public ExportData exportData(String taskId, boolean retainCitations) {
        DocumentEntity document = requireDocument(taskId);
        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        return new ExportData(document, retainCitations ? withCitationMarkers(document.getId(), current) : current);
    }

    public AiDisclosure aiDisclosure(String taskId) {
        DocumentEntity document = requireDocument(taskId);
        List<DocumentParagraphEntity> current = paragraphs(document.getId(), document.getCurrentVersion());
        long total = current.stream().mapToLong(p -> length(p.getPlainText())).sum();
        long ai = current.stream()
                .filter(p ->
                        p.getContentOrigin() != null && p.getContentOrigin().startsWith("AI"))
                .mapToLong(p -> length(p.getPlainText()))
                .sum();
        java.math.BigDecimal ratio = total == 0
                ? java.math.BigDecimal.ZERO
                : java.math.BigDecimal.valueOf(ai)
                        .divide(java.math.BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP);
        List<GenerationRecordEntity> records =
                generationRecordMapper.selectList(new LambdaQueryWrapper<GenerationRecordEntity>()
                        .eq(GenerationRecordEntity::getDocumentId, document.getId()));
        List<String> models = records.stream()
                .map(GenerationRecordEntity::getModelVersion)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new AiDisclosure(ratio, models, records.size());
    }

    private static long length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    private List<DocumentParagraphEntity> withCitationMarkers(String documentId, List<DocumentParagraphEntity> source) {
        List<DocumentCitationEntity> citations =
                citationMapper.selectList(new LambdaQueryWrapper<DocumentCitationEntity>()
                        .eq(DocumentCitationEntity::getDocumentId, documentId)
                        .orderByAsc(DocumentCitationEntity::getCreatedAt));
        if (citations.isEmpty()) return source;
        Map<String, List<DocumentCitationEntity>> byParagraph = citations.stream()
                .filter(citation -> citation.getReviewStatus() == null || "已引用".equals(citation.getReviewStatus()))
                .filter(citation -> citation.getKnowledgeDocumentId() == null
                        || knowledgeService.isCitationCurrent(citation.getKnowledgeDocumentId(),
                                citation.getKnowledgeChunkId(), citation.getKnowledgeDocumentVersion(),
                                citation.getSourceFileHash(), citation.getSourceExcerpt()))
                .collect(java.util.stream.Collectors.groupingBy(DocumentCitationEntity::getParagraphId));
        java.util.concurrent.atomic.AtomicInteger number = new java.util.concurrent.atomic.AtomicInteger();
        List<DocumentParagraphEntity> result = new ArrayList<>();
        for (DocumentParagraphEntity paragraph : source) {
            DocumentParagraphEntity copy = cloneParagraph(paragraph, paragraph.getVersionNumber());
            String text = paragraph.getPlainText();
            StringBuilder references = new StringBuilder();
            List<DocumentCitationEntity> values = byParagraph.getOrDefault(paragraph.getId(), List.of()).stream()
                    .filter(citation -> citation.getPositionStart() != null && citation.getPositionEnd() != null
                            && citation.getPositionStart() >= 0 && citation.getPositionEnd() <= text.length()
                            && citation.getPositionEnd() > citation.getPositionStart()
                            && text.substring(citation.getPositionStart(), citation.getPositionEnd())
                                    .equals(citation.getCitedFragment()))
                    .toList();
            record Positioned(int end, int number, DocumentCitationEntity citation) {}
            List<Positioned> positioned = values.stream()
                    .map(citation -> new Positioned(
                            Math.max(
                                    0,
                                    Math.min(
                                            text.length(),
                                            citation.getPositionEnd() == null
                                                    ? text.length()
                                                    : citation.getPositionEnd())),
                            number.incrementAndGet(),
                            citation))
                    .sorted(java.util.Comparator.comparingInt(Positioned::end).reversed())
                    .toList();
            StringBuilder marked = new StringBuilder(text);
            for (Positioned value : positioned) marked.insert(value.end(), "[" + value.number() + "]");
            positioned.stream()
                    .sorted(java.util.Comparator.comparingInt(Positioned::number))
                    .forEach(value -> references
                            .append("\n[")
                            .append(value.number())
                            .append(value.citation().getKnowledgeDocumentId() == null ? "] 材料 " : "] 知识原文 ")
                            .append(value.citation().getKnowledgeDocumentId() == null
                                    ? value.citation().getMaterialId()
                                    : value.citation().getKnowledgeDocumentId() + " V" + value.citation().getKnowledgeDocumentVersion())
                            .append("：")
                            .append(
                                    value.citation().getCitedFragment() == null
                                            ? ""
                                            : value.citation().getCitedFragment()));
            copy.setPlainText(marked + references.toString());
            // 引用标注插在纯文本位置上，带引用的段落按纯文本导出
            if (!positioned.isEmpty()) copy.setRichContent(null);
            result.add(copy);
        }
        return result;
    }

    private DocumentEntity createDocument(WritingTaskEntity task) {
        DocumentEntity document = new DocumentEntity();
        document.setTenantId(RequestContext.tenantId());
        document.setTaskId(task.getId());
        document.setTitle(task.getTaskName());
        document.setContentType("DOCX");
        document.setPlainTextCache("");
        document.setTotalWordCount(0);
        document.setSecurityLevel(task.getSecurityLevel());
        document.setCurrentVersion(1);
        document.setStatus("编辑中");
        documentMapper.insert(document);
        createVersion(document, "AI生成", "AI生成", "创建首个正文版本");
        return document;
    }

    private void beginVersion(DocumentEntity document, String versionType, String source, String description) {
        int current = document.getCurrentVersion();
        int next = current + 1;
        paragraphs(document.getId(), current).forEach(item -> insertParagraphClone(item, next));
        document.setCurrentVersion(next);
        createVersion(document, versionType, source, description);
    }

    private void createVersion(DocumentEntity document, String type, String source, String description) {
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setTenantId(RequestContext.tenantId());
        version.setDocumentId(document.getId());
        version.setVersionNumber(document.getCurrentVersion());
        version.setVersionType(type);
        version.setChangeSource(source);
        version.setChangeDescription(description);
        version.setWordCount(0);
        version.setIsRetained(true);
        versionMapper.insert(version);
    }

    private void refreshDocument(DocumentEntity document) {
        List<DocumentParagraphEntity> values = paragraphs(document.getId(), document.getCurrentVersion());
        String text = values.stream()
                .map(DocumentParagraphEntity::getPlainText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        int words = countWords(text);
        document.setPlainTextCache(text);
        document.setTotalWordCount(words);
        documentMapper.updateById(document);
        DocumentVersionEntity version = requireVersion(document.getId(), document.getCurrentVersion());
        version.setWordCount(words);
        versionMapper.updateById(version);
    }

    private String recordGeneration(
            String taskId,
            String documentId,
            String paragraphId,
            String type,
            String input,
            Generated generated,
            long started,
            boolean streaming) {
        String safeInput = generated.inputContext() == null ? (input == null ? "" : input) : generated.inputContext();
        String safeOutput = generated.output() == null ? "" : generated.output();
        GenerationRecordEntity record = new GenerationRecordEntity();
        record.setTenantId(RequestContext.tenantId());
        record.setTaskId(taskId);
        record.setDocumentId(documentId);
        record.setParagraphId(paragraphId);
        record.setGenerationType(type);
        record.setPromptTemplateId(generated.promptTemplateId());
        record.setPromptTemplateVersion(generated.promptTemplateVersion());
        record.setModelConfigId(generated.modelConfigId());
        record.setModelVersion(generated.modelVersion());
        record.setMaterialSnapshotVersion(generated.materialSnapshotVersion());
        record.setInputContextHash(sha256(safeInput));
        record.setOutputText(safeOutput);
        record.setOutputHash(sha256(safeOutput));
        record.setDurationMs(Math.toIntExact(Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000L)));
        record.setTokenCountInput(generated.inputTokens() > 0 ? generated.inputTokens() : estimateTokens(safeInput));
        record.setTokenCountOutput(
                generated.outputTokens() > 0 ? generated.outputTokens() : estimateTokens(safeOutput));
        record.setIsStreaming(streaming);
        record.setRetryCount(0);
        generationRecordMapper.insert(record);
        return record.getId();
    }

    private String regenerationContext(List<DocumentParagraphEntity> paragraphs, String nodeId, String instruction) {
        String existing = paragraphs.stream().filter(p -> nodeId.equals(p.getOutlineNodeId()))
                .map(p -> ("人工录入".equals(p.getContentOrigin()) ? "[人工修改]" : "") + p.getPlainText())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (existing.isBlank()) return instruction;
        return (instruction == null ? "" : instruction) + "\n重新生成要求：以下当前正文是待修改的草稿，不保证表达正确。保留明确的事实、请求意图和已核实数据，按本节提纲重新撰写。必须修正错别字、漏字、词序混乱、残缺句和重复表达；人工修改中的病句也必须修正，不得照抄错误表达。不能确定的金额、日期等事实不得猜测或补造。结束语应简洁完整，避免重复正文中的请求事项。输出可直接使用的本节正文，不输出修改说明。\n当前正文：\n" + existing;
    }

    private Generated generateSectionText(WritingTaskEntity task, WritingOutlineNodeEntity node, String instruction) {
        var materials = materialService.prepareGenerationContext(task.getId(), node.getId());
        String selectedKnowledge = writingKnowledgeService.generationContext(task.getId(), node.getId());
        String input = "文种：" + task.getDocumentTypeCode() + "\n主题：" + task.getWritingTopic() + "\n章节："
                + node.getNodeTitle() + "\n写作目的：" + task.getWritingPurpose()
                + "\n章节要点：" + node.getWritingPurpose() + "\n要求：" + (instruction == null ? "" : instruction)
                + "\n\n任务材料：\n" + materials.text()
                + "\n\n本节经人工选用的知识依据（只能使用这里列出的片段；写法参考不得作为事实）："
                + selectedKnowledge;
        Map<String, Object> variables = Map.of(
                "topic",
                task.getWritingTopic(),
                "section",
                node.getNodeTitle(),
                "instruction",
                instruction == null ? "" : instruction,
                "materials",
                materials.text() + "\n本节人工选用的知识依据：\n" + selectedKnowledge);
        ModelGenerationPort port = modelPort.getIfAvailable();
        if (port != null) {
            var result = port.generate(
                    "段落生成",
                    dataLevel(task.getSecurityLevel()),
                    task.getDocumentTypeCode(),
                    input,
                    variables,
                    "写作",
                    task.getId());
            if (result.isPresent()) return fromModel(result.get(), materials, input);
        }
        String fallbackInstruction = instruction == null ? null : instruction.split("\n重新生成要求：", 2)[0];
        String output = generator.generate(task, node, fallbackInstruction);
        return fallback("章节生成", output, materials, input);
    }

    private Generated transformText(
            String type, WritingTaskEntity task, String source, String instruction, Integer target) {
        var materials = materialService.prepareGenerationContext(task.getId());
        String input = "操作：" + type + "\n原文：" + source + "\n要求：" + (instruction == null ? "" : instruction) + "\n目标字数："
                + (target == null ? "未指定" : target) + "\n\n" + materials.text();
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("topic", task.getWritingTopic());
        variables.put("operation", type);
        variables.put("source", source);
        variables.put("instruction", instruction == null ? "" : instruction);
        variables.put("targetWordCount", target == null ? "" : target);
        variables.put("materials", materials.text());
        ModelGenerationPort port = modelPort.getIfAvailable();
        if (port != null) {
            var result = port.generate(
                    "续写".equals(type) ? "段落生成" : "文本改写",
                    dataLevel(task.getSecurityLevel()),
                    task.getDocumentTypeCode(),
                    input,
                    variables,
                    "写作",
                    task.getId());
            if (result.isPresent()) return fromModel(result.get(), materials, input);
        }
        return fallback(type, generator.transform(type, task, source, instruction, target), materials, input);
    }

    /** 划词候选：选中文字为改写对象，所在段落作为上下文，风格提示区分各候选。规则引擎兜底时按操作映射到整段改写规则。 */
    private Generated selectionText(
            String operation,
            WritingTaskEntity task,
            String paragraph,
            String selected,
            String instruction,
            String style) {
        var materials = materialService.prepareGenerationContext(task.getId());
        String request = (instruction == null ? "" : instruction.strip());
        String input = "操作：" + operation + "（只改写选中文字，输出可直接替换选中文字的内容，不要输出解释）\n选中文字：" + selected + "\n所在段落：" + paragraph
                + "\n表达风格：" + style + "\n要求：" + request + "\n\n" + materials.text();
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("topic", task.getWritingTopic());
        variables.put("operation", operation);
        variables.put("source", selected);
        variables.put("context", paragraph);
        variables.put("style", style);
        variables.put("instruction", request);
        variables.put("targetWordCount", "");
        variables.put("materials", materials.text());
        ModelGenerationPort port = modelPort.getIfAvailable();
        if (port != null) {
            var result = port.generate(
                    "文本改写",
                    dataLevel(task.getSecurityLevel()),
                    task.getDocumentTypeCode(),
                    input,
                    variables,
                    "写作",
                    task.getId());
            if (result.isPresent()) return fromModel(result.get(), materials, input);
        }
        String ruleType =
                switch (operation) {
                    case "扩写" -> "扩写";
                    case "缩写" -> "压缩";
                    default -> "改写";
                };
        return fallback(ruleType, generator.transform(ruleType, task, selected, instruction, null), materials, input);
    }

    private Generated fromModel(
            ModelGenerationPort.Result r, WritingMaterialService.GenerationContext materials, String input) {
        return new Generated(
                r.output(),
                r.modelConfigId(),
                r.modelVersion(),
                r.promptTemplateId(),
                r.promptTemplateVersion(),
                r.inputTokens(),
                r.outputTokens(),
                materials.snapshotVersion(),
                materials.materialIds(),
                input);
    }

    private Generated fallback(
            String type, String output, WritingMaterialService.GenerationContext materials, String input) {
        return new Generated(
                output,
                "rule-based-fallback",
                "1.0",
                "builtin-" + type,
                1,
                0,
                0,
                materials.snapshotVersion(),
                materials.materialIds(),
                input);
    }

    private String dataLevel(String value) {
        return switch (value == null ? "" : value) {
            case "普通", "公开" -> "普通";
            case "内部" -> "内部";
            case "敏感", "秘密" -> "敏感";
            case "涉密", "机密" -> "涉密";
            default -> "内部";
        };
    }

    private record Generated(
            String output,
            String modelConfigId,
            String modelVersion,
            String promptTemplateId,
            int promptTemplateVersion,
            int inputTokens,
            int outputTokens,
            int materialSnapshotVersion,
            List<String> sourceMaterialIds,
            String inputContext) {
        Generated withOutput(String value) {
            return new Generated(
                    value,
                    modelConfigId,
                    modelVersion,
                    promptTemplateId,
                    promptTemplateVersion,
                    inputTokens,
                    outputTokens,
                    materialSnapshotVersion,
                    sourceMaterialIds,
                    inputContext);
        }
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private int estimateTokens(String value) {
        return Math.max(1, (int) Math.ceil(value.codePointCount(0, value.length()) / 1.8));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int countWords(String text) {
        return Math.toIntExact(text.codePoints()
                .filter(value -> !Character.isWhitespace(value))
                .count());
    }

    private DocumentParagraphEntity cloneParagraph(DocumentParagraphEntity source, int version) {
        DocumentParagraphEntity copy = new DocumentParagraphEntity();
        copy.setTenantId(RequestContext.tenantId());
        copy.setDocumentId(source.getDocumentId());
        copy.setVersionNumber(version);
        copy.setOutlineNodeId(source.getOutlineNodeId());
        copy.setParagraphOrder(source.getParagraphOrder());
        copy.setContentSourceLevel(source.getContentSourceLevel());
        copy.setContentOrigin(source.getContentOrigin());
        copy.setSourceMaterialIds(source.getSourceMaterialIds());
        copy.setSourceKnowledgeIds(source.getSourceKnowledgeIds());
        copy.setIsLocked(source.getIsLocked());
        copy.setPlainText(source.getPlainText());
        copy.setRichContent(source.getRichContent());
        return copy;
    }

    private void insertParagraphClone(DocumentParagraphEntity source, int version) {
        DocumentParagraphEntity copy = cloneParagraph(source, version);
        paragraphMapper.insert(copy);
        cloneCitations(source.getId(), copy.getId(), source.getDocumentId());
    }

    private void cloneCitations(String sourceParagraphId, String targetParagraphId, String documentId) {
        cloneCitations(sourceParagraphId, targetParagraphId, documentId, false);
    }

    private void linkKnowledgeCandidates(String taskId, String nodeId, String documentId,
            DocumentParagraphEntity paragraph) {
        if (paragraph.getPlainText() == null || paragraph.getPlainText().isBlank()) return;
        for (var selected : writingKnowledgeService.selections(taskId, nodeId)) {
            if ("写法参考".equals(selected.getUsageType())) continue;
            DocumentCitationEntity candidate = new DocumentCitationEntity();
            candidate.setTenantId(RequestContext.tenantId());
            candidate.setDocumentId(documentId);
            candidate.setParagraphId(paragraph.getId());
            candidate.setKnowledgeDocumentId(selected.getKnowledgeDocumentId());
            candidate.setKnowledgeChunkId(selected.getChunkId());
            candidate.setKnowledgeDocumentVersion(selected.getDocumentVersion());
            candidate.setSourceFileHash(selected.getFileHash());
            candidate.setSourceExcerpt(selected.getExcerpt());
            candidate.setCitationType("候选依据");
            candidate.setCitedFragment(paragraph.getPlainText());
            candidate.setPositionStart(0);
            candidate.setPositionEnd(paragraph.getPlainText().length());
            candidate.setDisplayMode("内部留痕");
            candidate.setReviewStatus("待复核");
            citationMapper.insert(candidate);
        }
    }

    private void cloneCitations(String sourceParagraphId, String targetParagraphId, String documentId, boolean revised) {
        citationMapper
                .selectList(new LambdaQueryWrapper<DocumentCitationEntity>()
                        .eq(DocumentCitationEntity::getParagraphId, sourceParagraphId))
                .forEach(source -> {
                    DocumentCitationEntity copy = new DocumentCitationEntity();
                    copy.setTenantId(RequestContext.tenantId());
                    copy.setDocumentId(documentId);
                    copy.setParagraphId(targetParagraphId);
                    copy.setMaterialId(source.getMaterialId());
                    copy.setCitationType(source.getCitationType());
                    copy.setCitedFragment(source.getCitedFragment());
                    copy.setPositionStart(source.getPositionStart());
                    copy.setPositionEnd(source.getPositionEnd());
                    copy.setDisplayMode(source.getDisplayMode());
                    copy.setKnowledgeDocumentId(source.getKnowledgeDocumentId());
                    copy.setKnowledgeChunkId(source.getKnowledgeChunkId());
                    copy.setKnowledgeDocumentVersion(source.getKnowledgeDocumentVersion());
                    copy.setSourceFileHash(source.getSourceFileHash());
                    copy.setSourceExcerpt(source.getSourceExcerpt());
                    copy.setReviewStatus(revised ? "待复核" : source.getReviewStatus());
                    copy.setConfirmedBy(revised ? null : source.getConfirmedBy());
                    copy.setConfirmedAt(revised ? null : source.getConfirmedAt());
                    citationMapper.insert(copy);
                });
    }

    /**
     * 将任意版本的段落 ID 按“大纲节点 + 段落序号”映射到当前版本中的同一段落。页面停留在旧版本、或同一事务中连续修改同一段落时使用。
     */
    public DocumentParagraphEntity currentParagraphAt(String taskId, String paragraphId) {
        return currentParagraphAt(requireDocument(taskId), paragraphId);
    }

    private DocumentParagraphEntity currentParagraphAt(DocumentEntity document, String paragraphId) {
        DocumentParagraphEntity base = requireAnyVersionParagraph(document, paragraphId);
        return paragraphs(document.getId(), document.getCurrentVersion()).stream()
                .filter(p -> samePosition(p, base))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("段落在最新版本中已被删除或移动，请刷新后重试"));
    }

    public EditLeaseResponse acquireEditLease(String taskId, String paragraphId) {
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity paragraph = currentParagraphAt(document, paragraphId);
        String key = leaseKey(document, paragraph);
        String user = RequestContext.userId();
        if (!coordination.tryLock(key, user, EDIT_LEASE_TTL)) throw BusinessException.conflict(leaseHolderMessage(key));
        CoordinationStore.LockInfo info =
                coordination.inspect(key).orElseThrow(() -> BusinessException.conflict("段落编辑租约获取失败，请重试"));
        return new EditLeaseResponse(paragraph.getId(), info.owner(), info.expiresAt());
    }

    public void releaseEditLease(String taskId, String paragraphId) {
        DocumentEntity document = requireDocument(taskId);
        DocumentParagraphEntity paragraph = paragraphMapper.selectOne(new LambdaQueryWrapper<DocumentParagraphEntity>()
                .eq(DocumentParagraphEntity::getId, paragraphId)
                .eq(DocumentParagraphEntity::getDocumentId, document.getId()));
        if (paragraph == null) return;
        coordination.unlock(leaseKey(document, paragraph), RequestContext.userId());
    }

    /**
     * 每次保存都会克隆出新版本的段落行，旧段落 ID 随之失效。客户端基于旧版本编辑时，按“大纲节点 + 段落序号”定位当前版本中的同一段落：
     * 若该段落自客户端读取后未被他人修改，则把本次修改变基到当前版本；否则返回冲突，由用户在前端比对后选择覆盖或放弃。
     */
    private DocumentParagraphEntity resolveEditableParagraph(
            DocumentEntity document, String paragraphId, Integer lockVersion) {
        DocumentParagraphEntity base = requireAnyVersionParagraph(document, paragraphId);
        if (base.getVersionNumber().equals(document.getCurrentVersion())) {
            if (!base.getLockVersion().equals(lockVersion)) throw BusinessException.conflict("段落已被其他用户修改，请比对最新内容后再保存");
            return base;
        }
        DocumentParagraphEntity current = paragraphs(document.getId(), document.getCurrentVersion()).stream()
                .filter(p -> samePosition(p, base))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("段落在最新版本中已被删除或移动，请刷新后重试"));
        if (!Objects.equals(current.getPlainText(), base.getPlainText())
                || !Objects.equals(current.getRichContent(), base.getRichContent()))
            throw BusinessException.conflict("段落已被其他用户修改，请比对最新内容后再保存");
        return current;
    }

    private DocumentParagraphEntity requireAnyVersionParagraph(DocumentEntity document, String paragraphId) {
        DocumentParagraphEntity paragraph = paragraphMapper.selectOne(new LambdaQueryWrapper<DocumentParagraphEntity>()
                .eq(DocumentParagraphEntity::getId, paragraphId)
                .eq(DocumentParagraphEntity::getDocumentId, document.getId()));
        if (paragraph == null) throw BusinessException.notFound("段落不存在");
        return paragraph;
    }

    private static boolean samePosition(DocumentParagraphEntity a, DocumentParagraphEntity b) {
        return Objects.equals(a.getOutlineNodeId(), b.getOutlineNodeId())
                && Objects.equals(a.getParagraphOrder(), b.getParagraphOrder());
    }

    private void ensureNotLeasedByOthers(DocumentEntity document, DocumentParagraphEntity paragraph) {
        String key = leaseKey(document, paragraph);
        coordination
                .inspect(key)
                .filter(info -> !info.owner().equals(RequestContext.userId()))
                .ifPresent(info -> {
                    throw BusinessException.conflict(leaseHolderMessage(key));
                });
    }

    private String leaseHolderMessage(String key) {
        return coordination
                .inspect(key)
                .map(info -> "段落正在由用户 " + info.owner() + " 编辑，请稍后再试")
                .orElse("段落正在被其他用户编辑，请稍后再试");
    }

    private static String leaseKey(DocumentEntity document, DocumentParagraphEntity paragraph) {
        return ExclusiveGuard.tenantKey("doc:" + document.getId() + ":paragraph:" + paragraph.getOutlineNodeId() + ":"
                + paragraph.getParagraphOrder() + ":lease");
    }

    /** 串行化同一文稿上所有会产生新版本的写操作，避免并发计算出相同的版本号。 */
    private void lockDocumentWrites(String taskId) {
        guard.holdUntilTransactionEnd("writing:" + taskId + ":document-write", DOCUMENT_WRITE_TTL, "文稿正在被其他操作更新，请稍后重试");
    }

    private DocumentParagraphEntity requireCurrentParagraph(DocumentEntity document, String paragraphId) {
        DocumentParagraphEntity paragraph = paragraphMapper.selectOne(new LambdaQueryWrapper<DocumentParagraphEntity>()
                .eq(DocumentParagraphEntity::getId, paragraphId)
                .eq(DocumentParagraphEntity::getDocumentId, document.getId())
                .eq(DocumentParagraphEntity::getVersionNumber, document.getCurrentVersion()));
        if (paragraph == null) throw BusinessException.notFound("当前版本中不存在该段落");
        return paragraph;
    }

    private DocumentEntity requireDocument(String taskId) {
        taskService.require(taskId);
        DocumentEntity document = findDocument(taskId);
        if (document == null) throw BusinessException.notFound("文稿尚未生成");
        return document;
    }

    private DocumentEntity findDocument(String taskId) {
        return documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getTaskId, taskId));
    }

    private DocumentVersionEntity requireVersion(String documentId, int versionNumber) {
        DocumentVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, documentId)
                .eq(DocumentVersionEntity::getVersionNumber, versionNumber));
        if (version == null) throw BusinessException.notFound("文稿版本不存在");
        if (version.getContentPurgedAt() != null) throw BusinessException.conflict("该版本正文已按版本保留策略清理，仅保留版本元数据");
        return version;
    }

    /** 管理员保护关键版本，防止被保留策略清理；保护已标记待清理的版本时撤销标记（§14.4）。 */
    @Transactional
    public VersionResponse protectVersion(String taskId, int versionNumber, boolean protectedVersion) {
        DocumentEntity document = requireDocument(taskId);
        DocumentVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, document.getId())
                .eq(DocumentVersionEntity::getVersionNumber, versionNumber));
        if (version == null) throw BusinessException.notFound("文稿版本不存在");
        if (protectedVersion && version.getContentPurgedAt() != null)
            throw BusinessException.conflict("该版本正文已清理，无法再保护");
        version.setIsProtected(protectedVersion);
        if (protectedVersion) {
            version.setIsRetained(true);
            version.setRetentionMarkedAt(null);
        }
        versionMapper.updateById(version);
        return VersionResponse.from(version);
    }

    private List<DocumentParagraphEntity> paragraphs(String documentId, int version) {
        return paragraphMapper.selectList(new LambdaQueryWrapper<DocumentParagraphEntity>()
                .eq(DocumentParagraphEntity::getDocumentId, documentId)
                .eq(DocumentParagraphEntity::getVersionNumber, version)
                .orderByAsc(DocumentParagraphEntity::getParagraphOrder));
    }

    private DocumentEntity copyMetadata(DocumentEntity source) {
        DocumentEntity copy = new DocumentEntity();
        copy.setId(source.getId());
        copy.setTaskId(source.getTaskId());
        copy.setTitle(source.getTitle());
        copy.setContentType(source.getContentType());
        copy.setTotalWordCount(source.getTotalWordCount());
        copy.setSecurityLevel(source.getSecurityLevel());
        copy.setStatus(source.getStatus());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    public record ExportData(DocumentEntity document, List<DocumentParagraphEntity> paragraphs) {}

    /** §33 AI 生成内容标识：AI 参与内容的字数占比、使用过的模型版本和生成记录数。 */
    public record AiDisclosure(
            java.math.BigDecimal aiGeneratedRatio, List<String> modelVersions, long generationRecordsCount) {}
}
