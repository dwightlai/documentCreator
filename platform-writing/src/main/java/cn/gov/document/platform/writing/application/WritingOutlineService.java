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
        for (NodeDraft draft : drafts) {
            WritingOutlineNodeEntity node = new WritingOutlineNodeEntity();
            node.setTenantId(RequestContext.tenantId());
            node.setOutlineId(outline.getId());
            node.setNodeOrder(order++);
            node.setNodeTitle(draft.title());
            node.setWritingPurpose(draft.purpose());
            node.setTargetWordCount(draft.targetWordCount());
            node.setIsRequired(draft.required());
            node.setReferencedMaterialIds("[]");
            node.setGenerationStatus("待生成");
            nodeMapper.insert(node);
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
        if (nodeMapper.selectCount(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                        .eq(WritingOutlineNodeEntity::getParentNodeId, nodeId))
                > 0) throw BusinessException.conflict("请先删除或移动子节点");
        nodeMapper.deleteById(node.getId());
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
        boolean valid = response.nodes().size() >= 2
                && response.nodes().stream()
                        .allMatch(node -> node.title() != null && !node.title().isBlank());
        WritingOutlineEntity outline = outlineMapper.selectById(response.id());
        outline.setStructureCheckResult(valid ? "通过：结构完整，可确认" : "不通过：至少需要两个具名节点");
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
        while (cursor != null) {
            if (cursor.equals(selfId)) throw BusinessException.unprocessable("不能形成大纲节点循环");
            WritingOutlineNodeEntity next = nodeMapper.selectById(cursor);
            cursor = next == null ? null : next.getParentNodeId();
        }
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
        List<WritingOutlineNodeEntity> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WritingOutlineNodeEntity>()
                .eq(WritingOutlineNodeEntity::getOutlineId, outlineId)
                .orderByAsc(WritingOutlineNodeEntity::getNodeOrder));
        int order = 1;
        for (WritingOutlineNodeEntity node : nodes) {
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
    }

    private String write(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
