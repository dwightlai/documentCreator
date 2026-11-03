package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.knowledge.application.KnowledgeService;
import cn.gov.document.platform.writing.api.WritingKnowledgeDtos;
import cn.gov.document.platform.writing.domain.WritingKnowledgeSelectionEntity;
import cn.gov.document.platform.writing.domain.WritingOutlineEntity;
import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.persistence.WritingKnowledgeSelectionMapper;
import cn.gov.document.platform.writing.persistence.WritingOutlineMapper;
import cn.gov.document.platform.writing.persistence.WritingOutlineNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WritingKnowledgeService {
    private final WritingKnowledgeSelectionMapper mapper;
    private final WritingOutlineNodeMapper nodeMapper;
    private final WritingOutlineMapper outlineMapper;
    private final WritingTaskService tasks;
    private final KnowledgeService knowledge;

    public WritingKnowledgeService(WritingKnowledgeSelectionMapper mapper, WritingOutlineNodeMapper nodeMapper,
            WritingOutlineMapper outlineMapper,
            WritingTaskService tasks, KnowledgeService knowledge) {
        this.mapper = mapper;
        this.nodeMapper = nodeMapper;
        this.outlineMapper = outlineMapper;
        this.tasks = tasks;
        this.knowledge = knowledge;
    }

    @Transactional
    public WritingKnowledgeDtos.SelectionResponse select(String taskId, WritingKnowledgeDtos.SelectRequest request) {
        tasks.require(taskId);
        WritingOutlineNodeEntity node = nodeMapper.selectById(request.outlineNodeId());
        if (node == null)
            throw BusinessException.unprocessable("请先选择提纲章节");
        WritingOutlineEntity outline = outlineMapper.selectById(node.getOutlineId());
        if (outline == null || !taskId.equals(outline.getTaskId()))
            throw BusinessException.unprocessable("请先选择当前任务的提纲章节");
        var excerpt = knowledge.requireWritingExcerpt(request.sourceId(), request.documentId(), request.chunkId(),
                request.usageType());
        if (!request.applicabilityConfirmed())
            throw BusinessException.unprocessable("请先核对本任务的适用地区、对象、事项范围与资料时效");
        if (request.selectionReason().trim().length() > 500)
            throw BusinessException.unprocessable("选用理由不能超过 500 字");
        long duplicates = mapper.selectCount(new LambdaQueryWrapper<WritingKnowledgeSelectionEntity>()
                .eq(WritingKnowledgeSelectionEntity::getTaskId, taskId)
                .eq(WritingKnowledgeSelectionEntity::getOutlineNodeId, node.getId())
                .eq(WritingKnowledgeSelectionEntity::getChunkId, excerpt.chunkId()));
        if (duplicates > 0) throw BusinessException.conflict("本节已选用该原文片段");
        WritingKnowledgeSelectionEntity e = new WritingKnowledgeSelectionEntity();
        e.setTenantId(RequestContext.tenantId());
        e.setTaskId(taskId);
        e.setOutlineNodeId(node.getId());
        e.setSourceId(excerpt.sourceId());
        e.setKnowledgeDocumentId(excerpt.documentId());
        e.setChunkId(excerpt.chunkId());
        e.setDocumentTitle(excerpt.documentTitle());
        e.setDocumentVersion(excerpt.documentVersion());
        e.setUsageType(excerpt.usageType());
        e.setCompleteness(excerpt.completeness());
        e.setExcerpt(excerpt.text());
        e.setFileHash(excerpt.fileHash());
        e.setSelectedBy(RequestContext.userId());
        e.setSelectionReason(request.selectionReason().trim());
        e.setSelectedAt(OffsetDateTime.now());
        mapper.insert(e);
        return WritingKnowledgeDtos.SelectionResponse.from(e);
    }

    public List<WritingKnowledgeDtos.SelectionResponse> list(String taskId, String nodeId) {
        tasks.require(taskId);
        return selections(taskId, nodeId).stream().map(WritingKnowledgeDtos.SelectionResponse::from).toList();
    }

    public List<WritingKnowledgeSelectionEntity> selections(String taskId, String nodeId) {
        return mapper.selectList(new LambdaQueryWrapper<WritingKnowledgeSelectionEntity>()
                .eq(WritingKnowledgeSelectionEntity::getTaskId, taskId)
                .eq(nodeId != null && !nodeId.isBlank(), WritingKnowledgeSelectionEntity::getOutlineNodeId, nodeId)
                .orderByAsc(WritingKnowledgeSelectionEntity::getSelectedAt));
    }

    @Transactional
    public void remove(String taskId, String selectionId) {
        tasks.require(taskId);
        WritingKnowledgeSelectionEntity e = mapper.selectById(selectionId);
        if (e == null || !taskId.equals(e.getTaskId())) throw BusinessException.notFound("选用记录不存在");
        if (!RequestContext.userId().equals(e.getSelectedBy()))
            throw BusinessException.unprocessable("请由选用该依据的写作者移除");
        mapper.deleteById(selectionId);
    }

    public WritingKnowledgeSelectionEntity requireSelection(String taskId, String selectionId) {
        tasks.require(taskId);
        WritingKnowledgeSelectionEntity e = mapper.selectById(selectionId);
        if (e == null || !taskId.equals(e.getTaskId())) throw BusinessException.notFound("选用记录不存在");
        if (!RequestContext.userId().equals(e.getSelectedBy()))
            throw BusinessException.unprocessable("请由选用该依据的写作者完成本次操作");
        var current = knowledge.requireWritingExcerpt(e.getSourceId(), e.getKnowledgeDocumentId(), e.getChunkId(),
                e.getUsageType());
        if (current.documentVersion() != e.getDocumentVersion()
                || !Objects.equals(current.fileHash(), e.getFileHash())
                || !current.text().equals(e.getExcerpt()))
            throw BusinessException.conflict("知识文档已变化，请重新核对并选用");
        return e;
    }

    public WritingKnowledgeSelectionEntity requireOriginalSelection(String taskId, String selectionId) {
        WritingKnowledgeSelectionEntity e = requireSelection(taskId, selectionId);
        var current = knowledge.requireWritingExcerpt(e.getSourceId(), e.getKnowledgeDocumentId(), e.getChunkId(), e.getUsageType());
        if (!"本地原文可用".equals(current.completeness()))
            throw BusinessException.unprocessable("直接摘录需要可核对的本地完整原文；当前资料仅可参考或起草待核表述");
        return e;
    }

    /** Generation runs in a job thread; recheck the stored version and content before reading the snapshot. */
    public String generationContext(String taskId, String nodeId) {
        StringBuilder result = new StringBuilder();
        for (WritingKnowledgeSelectionEntity e : selections(taskId, nodeId)) {
            if (!RequestContext.userId().equals(e.getSelectedBy()))
                throw BusinessException.unprocessable("本节有其他用户选用的依据，请由当前写作者重新核对");
            var current = knowledge.requireWritingExcerptForGeneration(e.getSourceId(), e.getKnowledgeDocumentId(),
                    e.getChunkId(), e.getUsageType());
            if (current.documentVersion() != e.getDocumentVersion()
                    || !Objects.equals(current.fileHash(), e.getFileHash())
                    || !current.text().equals(e.getExcerpt()))
                throw BusinessException.conflict("已选用的知识文档发生变化，请重新核对并选用：" + e.getDocumentTitle());
            String text = "写法参考".equals(e.getUsageType()) ? styleGuidance(e.getExcerpt()) : e.getExcerpt();
            if (text.length() > 1800) text = text.substring(0, 1800);
            result.append("\n【").append(e.getUsageType()).append("；来源：").append(e.getDocumentTitle())
                    .append("；版本 V").append(e.getDocumentVersion()).append("；选用 ID：")
                    .append(e.getId()).append("】\n").append(text).append('\n');
        }
        return result.toString();
    }

    public static String styleGuidance(String excerpt) {
        String[] paragraphs = excerpt.split("[\\r\\n]+");
        long nonblank = java.util.Arrays.stream(paragraphs).filter(p -> !p.isBlank()).count();
        int average = nonblank == 0 ? 0 : excerpt.length() / (int) nonblank;
        boolean numbered = excerpt.matches("(?s).*[一二三四五六七八九十][、．.].*") || excerpt.matches("(?s).*\\d+[、．.].*");
        return "只参考行文组织：约 " + nonblank + " 个段落，平均每段约 " + average
                + " 字，" + (numbered ? "使用条列结构" : "使用连续段落")
                + "。不得复制原文中的人名、单位、数值、事项或政策结论。";
    }

}
