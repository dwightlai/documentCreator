package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeDraft;
import cn.gov.document.platform.writing.api.WritingOutlineDtos.Response;
import cn.gov.document.platform.writing.domain.WritingOutlineEntity;
import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import cn.gov.document.platform.writing.domain.WritingTaskStatus;
import cn.gov.document.platform.writing.persistence.WritingOutlineMapper;
import cn.gov.document.platform.writing.persistence.WritingOutlineNodeMapper;
import cn.gov.document.platform.writing.persistence.WritingTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WritingOutlineService {
    private final WritingOutlineMapper outlineMapper;
    private final WritingOutlineNodeMapper nodeMapper;
    private final WritingTaskMapper taskMapper;
    private final WritingTaskService taskService;
    private final WritingMaterialService materialService;
    private final WritingAssistant assistant;
    private final ObjectMapper objectMapper;

    public WritingOutlineService(
            WritingOutlineMapper outlineMapper,
            WritingOutlineNodeMapper nodeMapper,
            WritingTaskMapper taskMapper,
            WritingTaskService taskService,
            WritingMaterialService materialService,
            WritingAssistant assistant,
            ObjectMapper objectMapper) {
        this.outlineMapper = outlineMapper;
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.materialService = materialService;
        this.assistant = assistant;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Response generate(String taskId) {
        WritingTaskEntity task = taskService.require(taskId);
        int nextVersion = Math.toIntExact(outlineMapper.selectCount(
                        new LambdaQueryWrapper<WritingOutlineEntity>().eq(WritingOutlineEntity::getTaskId, taskId)))
                + 1;
        WritingOutlineEntity outline = new WritingOutlineEntity();
        outline.setTenantId(RequestContext.tenantId());
        outline.setTaskId(taskId);
        outline.setOutlineVersion(nextVersion);
        outline.setIsConfirmed(false);
        outline.setStructureCheckResult("待检查");
        outlineMapper.insert(outline);

        List<NodeDraft> drafts = assistant.generateOutline(task, Math.toIntExact(materialService.countUsable(taskId)));
        int order = 1;
        String[] parents = new String[OutlineStructure.MAX_LEVEL + 1];
        for (NodeDraft draft : drafts) {
            int level = Math.max(1, Math.min(draft.level(), OutlineStructure.MAX_LEVEL));
            WritingOutlineNodeEntity node = new WritingOutlineNodeEntity();
            node.setTenantId(RequestContext.tenantId());
            node.setOutlineId(outline.getId());
            node.setParentNodeId(level == 1 ? null : parents[level - 1]);
            node.setNodeOrder(order++);
            node.setNodeTitle(draft.title());
            node.setWritingPurpose(draft.purpose());
            node.setTargetWordCount(draft.targetWordCount());
            node.setIsRequired(draft.required());
            node.setReferencedMaterialIds("[]");
            node.setGenerationStatus("待生成");
            nodeMapper.insert(node);
            parents[level] = node.getId();
        }
        task.setStatus(WritingTaskStatus.OUTLINE_PENDING.code());
        taskMapper.updateById(task);
        return get(taskId, nextVersion);
    }

    public Response get(String taskId, int version) {
        taskService.require(taskId);
        WritingOutlineEntity outline = outlineMapper.selectOne(new LambdaQueryWrapper<WritingOutlineEntity>()
                .eq(WritingOutlineEntity::getTaskId, taskId)
                .eq(WritingOutlineEntity::getOutlineVersion, version));
        if (outline == null) throw BusinessException.notFound("大纲版本不存在");
        List<WritingOutlineNodeEntity> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                .eq(WritingOutlineNodeEntity::getOutlineId, outline.getId())
                .orderByAsc(WritingOutlineNodeEntity::getNodeOrder));
        return Response.from(outline, nodes);
    }

    public Response current(String taskId) {
        taskService.require(taskId);
        WritingOutlineEntity outline = outlineMapper.selectOne(new LambdaQueryWrapper<WritingOutlineEntity>()
                .eq(WritingOutlineEntity::getTaskId, taskId)
                .orderByDesc(WritingOutlineEntity::getOutlineVersion)
                .last("LIMIT 1"));
        if (outline == null) throw BusinessException.notFound("尚未生成大纲");
        return get(taskId, outline.getOutlineVersion());
    }

    @Transactional
    public Response addNode(
            String taskId, int version, cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeRequest request) {
        WritingOutlineEntity outline = requireEditable(taskId, version);
        validateParent(outline, request.parentNodeId(), null);
        shiftOrders(outline.getId(), request.order(), 1, null);
        WritingOutlineNodeEntity node = new WritingOutlineNodeEntity();
        node.setTenantId(RequestContext.tenantId());
        node.setOutlineId(outline.getId());
        applyNode(node, request);
        node.setGenerationStatus("待生成");
        nodeMapper.insert(node);
        normalizeOrders(outline.getId());
        return get(taskId, version);
    }

    @Transactional
    public Response updateNode(
            String taskId,
            int version,
            String nodeId,
            cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeRequest request) {
        WritingOutlineEntity outline = requireEditable(taskId, version);
        WritingOutlineNodeEntity node = requireNode(outline, nodeId);
        validateParent(outline, request.parentNodeId(), nodeId);
        if (request.lockVersion() == null) throw BusinessException.unprocessable("修改节点必须提交 lockVersion");
        int oldOrder = node.getNodeOrder();
        node.setLockVersion(request.lockVersion());
        applyNode(node, request);
        node.setGenerationStatus("已编辑");
        if (nodeMapper.updateById(node) != 1) throw BusinessException.conflict("大纲节点已被其他用户修改");
        if (oldOrder != request.order()) reorderNode(outline.getId(), node, request.order());
        else normalizeOrders(outline.getId());
        return get(taskId, version);
    }

    @Transactional
    public Response deleteNode(String taskId, int version, String nodeId) {
        WritingOutlineEntity outline = requireEditable(taskId, version);
        WritingOutlineNodeEntity node = requireNode(outline, nodeId);
        OutlineStructure structure = new OutlineStructure(nodes(outline.getId()));
        java.util.ArrayDeque<WritingOutlineNodeEntity> pending = new java.util.ArrayDeque<>(List.of(node));
        while (!pending.isEmpty()) {
            WritingOutlineNodeEntity current = pending.pop();
            pending.addAll(structure.children(current.getId()));
            nodeMapper.deleteById(current.getId());
        }
        normalizeOrders(outline.getId());
        return get(taskId, version);
    }

    @Transactional
    public Response regenerateNode(String taskId, int version, String nodeId) {
        WritingOutlineEntity outline = requireEditable(taskId, version);
        WritingOutlineNodeEntity node = requireNode(outline, nodeId);
        WritingTaskEntity task = taskService.require(taskId);
        List<NodeDraft> drafts = assistant.generateOutline(task, Math.toIntExact(materialService.countUsable(taskId)));
        NodeDraft draft = drafts.get(Math.floorMod(node.getNodeOrder() - 1, drafts.size()));
        node.setNodeTitle(draft.title());
        node.setWritingPurpose(draft.purpose());
        node.setTargetWordCount(draft.targetWordCount());
        node.setIsRequired(draft.required());
        node.setGenerationStatus("待生成");
        nodeMapper.updateById(node);
        return get(taskId, version);
    }

    @Transactional
    public Response structureCheck(String taskId, int version) {
        Response response = get(taskId, version);
        WritingOutlineEntity outline = outlineMapper.selectById(response.id());
        outline.setStructureCheckResult(checkStructure(outline.getId()));
        outlineMapper.updateById(outline);
        return get(taskId, version);
    }

    @Transactional
    public Response confirm(String taskId, int version) {
        Response checked = structureCheck(taskId, version);
        if (!checked.structureCheckResult().startsWith("通过")) {
            throw BusinessException.unprocessable(checked.structureCheckResult());
        }
        WritingOutlineEntity outline = outlineMapper.selectById(checked.id());
        outline.setIsConfirmed(true);
        outline.setConfirmedBy(RequestContext.userId());
        outline.setConfirmedAt(OffsetDateTime.now());
        outlineMapper.updateById(outline);

        WritingTaskEntity task = taskService.require(taskId);
        task.setOutlineConfirmedVersion(version);
        task.setStatus(WritingTaskStatus.GENERATING.code());
        taskMapper.updateById(task);
        return get(taskId, version);
    }

    private String checkStructure(String outlineId) {
        List<WritingOutlineNodeEntity> nodes = nodes(outlineId);
        OutlineStructure structure = new OutlineStructure(nodes);
        if (structure.children(null).size() < 2) return "不通过：至少需要两个一级章节";
        for (WritingOutlineNodeEntity node : structure.ordered()) {
            if (node.getNodeTitle() == null || node.getNodeTitle().isBlank()) return "不通过：存在未命名章节";
            if (structure.level(node.getId()) > OutlineStructure.MAX_LEVEL)
                return "不通过：「" + node.getNodeTitle() + "」超过三级，公文正文层次最多用到“1.”";
            if (structure.children(node.getId()).size() == 1)
                return "不通过：「" + node.getNodeTitle() + "」只有一个下级章节，应至少分为两项或取消分级";
        }
        return "通过：结构完整，可确认";
    }

    private List<WritingOutlineNodeEntity> nodes(String outlineId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                .eq(WritingOutlineNodeEntity::getOutlineId, outlineId)
                .orderByAsc(WritingOutlineNodeEntity::getNodeOrder));
    }

    private WritingOutlineEntity requireEditable(String taskId, int version) {
        taskService.require(taskId);
        WritingOutlineEntity outline = outlineMapper.selectOne(new LambdaQueryWrapper<WritingOutlineEntity>()
                .eq(WritingOutlineEntity::getTaskId, taskId)
                .eq(WritingOutlineEntity::getOutlineVersion, version));
        if (outline == null) throw BusinessException.notFound("大纲版本不存在");
        if (Boolean.TRUE.equals(outline.getIsConfirmed())) throw BusinessException.conflict("已确认大纲不可修改，请生成新版本");
        return outline;
    }

    private WritingOutlineNodeEntity requireNode(WritingOutlineEntity outline, String id) {
        WritingOutlineNodeEntity node = nodeMapper.selectOne(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                .eq(WritingOutlineNodeEntity::getId, id)
                .eq(WritingOutlineNodeEntity::getOutlineId, outline.getId()));
        if (node == null) throw BusinessException.notFound("大纲节点不存在");
        return node;
    }

    private void validateParent(WritingOutlineEntity outline, String parentId, String selfId) {
        if (parentId == null || parentId.isBlank()) return;
        if (parentId.equals(selfId)) throw BusinessException.unprocessable("节点不能以自身为父节点");
        WritingOutlineNodeEntity parent = requireNode(outline, parentId);
        String cursor = parent.getParentNodeId();
        int parentLevel = 1;
        while (cursor != null) {
            if (cursor.equals(selfId)) throw BusinessException.unprocessable("不能形成大纲节点循环");
            WritingOutlineNodeEntity next = nodeMapper.selectById(cursor);
            cursor = next == null ? null : next.getParentNodeId();
            parentLevel++;
        }
        int height = selfId == null ? 1 : height(new OutlineStructure(nodes(outline.getId())), selfId);
        if (parentLevel + height > OutlineStructure.MAX_LEVEL) throw BusinessException.unprocessable("提纲最多三级");
    }

    private int height(OutlineStructure structure, String nodeId) {
        return 1 + structure.children(nodeId).stream()
                .mapToInt(child -> height(structure, child.getId()))
                .max()
                .orElse(0);
    }

    private void applyNode(
            WritingOutlineNodeEntity node,
            cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeRequest request) {
        node.setParentNodeId(request.parentNodeId());
        node.setNodeOrder(request.order());
        node.setNodeTitle(request.title().trim());
        node.setWritingPurpose(request.purpose());
        node.setKeyFacts(request.keyFacts());
        node.setReferencedMaterialIds(
                write(request.referencedMaterialIds() == null ? List.of() : request.referencedMaterialIds()));
        node.setTargetWordCount(request.targetWordCount());
        node.setIsRequired(request.required() == null || request.required());
        node.setHumanNote(request.humanNote());
    }

    private void shiftOrders(String outlineId, int from, int amount, String excludedId) {
        nodeMapper
                .selectList(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                        .eq(WritingOutlineNodeEntity::getOutlineId, outlineId)
                        .ge(WritingOutlineNodeEntity::getNodeOrder, from)
                        .orderByDesc(WritingOutlineNodeEntity::getNodeOrder))
                .stream()
                .filter(item -> !item.getId().equals(excludedId))
                .forEach(item -> {
                    item.setNodeOrder(item.getNodeOrder() + amount);
                    nodeMapper.updateById(item);
                });
    }

    private void normalizeOrders(String outlineId) {
        int order = 1;
        for (WritingOutlineNodeEntity node : new OutlineStructure(nodes(outlineId)).ordered()) {
            if (node.getNodeOrder() != order) {
                node.setNodeOrder(order);
                nodeMapper.updateById(node);
            }
            order++;
        }
    }

    private void reorderNode(String outlineId, WritingOutlineNodeEntity moved, int requested) {
        List<WritingOutlineNodeEntity> ordered =
                new java.util.ArrayList<>(nodeMapper.selectList(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                        .eq(WritingOutlineNodeEntity::getOutlineId, outlineId)
                        .ne(WritingOutlineNodeEntity::getId, moved.getId())
                        .orderByAsc(WritingOutlineNodeEntity::getNodeOrder)));
        ordered.add(Math.min(Math.max(0, requested - 1), ordered.size()), moved);
        int order = 1;
        for (WritingOutlineNodeEntity node : ordered) {
            node.setNodeOrder(order++);
            nodeMapper.updateById(node);
        }
        normalizeOrders(outlineId);
    }

    private String write(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
