package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.knowledge.application.KnowledgeService;
import cn.gov.document.platform.writing.api.WritingMaterialDtos.CreateRequest;
import cn.gov.document.platform.writing.api.WritingMaterialDtos.Response;
import cn.gov.document.platform.writing.api.WritingMaterialDtos.UpdateRequest;
import cn.gov.document.platform.writing.domain.WritingMaterialEntity;
import cn.gov.document.platform.writing.domain.WritingMaterialSnapshotEntity;
import cn.gov.document.platform.writing.persistence.WritingMaterialMapper;
import cn.gov.document.platform.writing.persistence.WritingMaterialSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WritingMaterialService {
    private final WritingMaterialMapper mapper;
    private final WritingTaskService taskService;
    private final WritingMaterialSnapshotMapper snapshotMapper;
    private final KnowledgeService knowledge;

    public WritingMaterialService(
            WritingMaterialMapper mapper,
            WritingTaskService taskService,
            WritingMaterialSnapshotMapper snapshotMapper,
            KnowledgeService knowledge) {
        this.mapper = mapper;
        this.taskService = taskService;
        this.snapshotMapper = snapshotMapper;
        this.knowledge = knowledge;
    }

    @Transactional
    public Response attachKnowledge(String taskId, cn.gov.document.platform.writing.api.WritingMaterialDtos.FromKnowledgeRequest request) {
        taskService.require(taskId);
        var read = knowledge.readDocument(request.sourceId(), request.documentId());
        var document = read.document();
        if (!"已完成".equals(document.parsedStatus()) || read.chunks().isEmpty()
                || "来源索引".equals(document.completeness()))
            throw BusinessException.unprocessable("该知识文档尚无可选正文");
        var selected = request.chunkId() == null || request.chunkId().isBlank()
                ? read.chunks()
                : read.chunks().stream().filter(chunk -> chunk.id().equals(request.chunkId())).toList();
        if (selected.isEmpty()) throw BusinessException.notFound("知识片段不存在");
        String text = selected.stream().map(c -> c.chunkText()).collect(java.util.stream.Collectors.joining("\n\n"));
        if (text.length() > 200_000) throw BusinessException.unprocessable("整篇内容超过 20 万字，请改选片段");
        WritingMaterialEntity entity = new WritingMaterialEntity();
        entity.setTenantId(RequestContext.tenantId());
        entity.setTaskId(taskId);
        entity.setOutlineNodeId(StringUtils.hasText(request.outlineNodeId()) ? request.outlineNodeId().trim() : null);
        entity.setMaterialName(document.documentTitle() + (request.chunkId() == null || request.chunkId().isBlank() ? "（整篇）" : "（片段 " + selected.get(0).chunkOrder() + "）"));
        entity.setSourceType("知识库参考");
        entity.setSourceId(document.id());
        entity.setAuthorityLevel(document.authorityLevel());
        entity.setUsageMode(request.chunkId() == null || request.chunkId().isBlank() ? "全文参考" : "指定片段");
        entity.setCitationPurpose("待核资料");
        entity.setPermissionStatus("仅供参考");
        entity.setSelectedFragment(text);
        entity.setIsLocked(true);
        entity.setFileHash(document.originalFileHash());
        mapper.insert(entity);
        return Response.from(entity);
    }

    @Transactional
    public Response create(String taskId, CreateRequest request) {
        taskService.require(taskId);
        if ("知识库".equals(request.sourceType()) || "知识库参考".equals(request.sourceType()))
            throw BusinessException.unprocessable("知识库材料请从文档或切片选取，不能手填来源");
        if (request.effectiveDate() != null
                && request.expiryDate() != null
                && request.expiryDate().isBefore(request.effectiveDate())) {
            throw BusinessException.unprocessable("材料失效日期不得早于生效日期");
        }
        WritingMaterialEntity entity = new WritingMaterialEntity();
        entity.setTenantId(RequestContext.tenantId());
        entity.setTaskId(taskId);
        entity.setMaterialName(request.materialName().trim());
        entity.setSourceType(request.sourceType());
        entity.setSourceId(request.sourceId());
        entity.setAuthorityLevel(request.authorityLevel());
        entity.setUsageMode(request.usageMode());
        entity.setCitationPurpose(request.citationPurpose());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpiryDate(request.expiryDate());
        entity.setPermissionStatus(StringUtils.hasText(request.permissionStatus()) ? request.permissionStatus() : "允许");
        entity.setSelectedFragment(request.selectedFragment());
        entity.setIsLocked(Boolean.TRUE.equals(request.locked()));
        entity.setFileHash(request.fileHash());
        entity.setFilePath(request.filePath());
        entity.setOutlineNodeId(StringUtils.hasText(request.outlineNodeId()) ? request.outlineNodeId().trim() : null);
        mapper.insert(entity);
        return Response.from(entity);
    }

    public List<Response> list(String taskId) {
        taskService.require(taskId);
        return mapper
                .selectList(new LambdaQueryWrapper<WritingMaterialEntity>()
                        .eq(WritingMaterialEntity::getTaskId, taskId)
                        .orderByAsc(WritingMaterialEntity::getCreatedAt))
                .stream()
                .map(Response::from)
                .toList();
    }

    @Transactional
    public Response update(String taskId, String materialId, UpdateRequest request) {
        taskService.require(taskId);
        WritingMaterialEntity entity = requireOwned(taskId, materialId);
        validateDates(request.effectiveDate(), request.expiryDate());
        entity.setLockVersion(request.lockVersion());
        entity.setMaterialName(request.materialName().trim());
        entity.setAuthorityLevel(request.authorityLevel());
        entity.setUsageMode(request.usageMode());
        entity.setCitationPurpose(request.citationPurpose());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpiryDate(request.expiryDate());
        entity.setPermissionStatus(StringUtils.hasText(request.permissionStatus()) ? request.permissionStatus() : "允许");
        entity.setSelectedFragment(request.selectedFragment());
        entity.setIsLocked(Boolean.TRUE.equals(request.locked()));
        entity.setFileHash(request.fileHash());
        entity.setFilePath(request.filePath());
        if (mapper.updateById(entity) != 1) throw BusinessException.conflict("材料已被其他用户修改");
        return Response.from(mapper.selectById(materialId));
    }

    @Transactional
    public cn.gov.document.platform.writing.api.WritingMaterialDtos.SnapshotResponse createSnapshot(String taskId) {
        taskService.require(taskId);
        List<WritingMaterialEntity> materials = mapper.selectList(
                new LambdaQueryWrapper<WritingMaterialEntity>().eq(WritingMaterialEntity::getTaskId, taskId));
        if (materials.isEmpty()) throw BusinessException.unprocessable("材料清单为空，无法创建快照");
        int version = snapshotMapper
                .selectList(new LambdaQueryWrapper<WritingMaterialSnapshotEntity>()
                        .eq(WritingMaterialSnapshotEntity::getTaskId, taskId)
                        .orderByDesc(WritingMaterialSnapshotEntity::getSnapshotVersion)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(item -> item.getSnapshotVersion() + 1)
                .orElse(1);
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        for (WritingMaterialEntity material : materials) {
            WritingMaterialSnapshotEntity snapshot = new WritingMaterialSnapshotEntity();
            snapshot.setTenantId(RequestContext.tenantId());
            snapshot.setTaskId(taskId);
            snapshot.setSnapshotVersion(version);
            snapshot.setMaterialId(material.getId());
            snapshot.setMaterialVersion(material.getLockVersion() + 1);
            snapshot.setFileHash(material.getFileHash());
            snapshot.setSelectedFragment(material.getSelectedFragment());
            snapshot.setRetrievalTime(now);
            snapshot.setPermissionCheckResult(permissionResult(material));
            snapshotMapper.insert(snapshot);
        }
        return snapshot(taskId, version);
    }

    public cn.gov.document.platform.writing.api.WritingMaterialDtos.SnapshotResponse snapshot(
            String taskId, int version) {
        taskService.require(taskId);
        List<WritingMaterialSnapshotEntity> rows =
                snapshotMapper.selectList(new LambdaQueryWrapper<WritingMaterialSnapshotEntity>()
                        .eq(WritingMaterialSnapshotEntity::getTaskId, taskId)
                        .eq(WritingMaterialSnapshotEntity::getSnapshotVersion, version)
                        .orderByAsc(WritingMaterialSnapshotEntity::getCreatedAt));
        if (rows.isEmpty()) throw BusinessException.notFound("材料快照不存在");
        long denied = rows.stream()
                .filter(item -> "禁止".equals(item.getPermissionCheckResult()))
                .count();
        return new cn.gov.document.platform.writing.api.WritingMaterialDtos.SnapshotResponse(
                taskId,
                version,
                rows.stream()
                        .map(cn.gov.document.platform.writing.api.WritingMaterialDtos.SnapshotItem::from)
                        .toList(),
                rows.size() - denied,
                denied);
    }

    public long countUsable(String taskId) {
        return mapper.selectCount(new LambdaQueryWrapper<WritingMaterialEntity>()
                .eq(WritingMaterialEntity::getTaskId, taskId)
                .eq(WritingMaterialEntity::getPermissionStatus, "允许"));
    }

    public GenerationContext prepareGenerationContext(String taskId) {
        return prepareGenerationContext(taskId, null);
    }

    @Transactional
    public GenerationContext prepareGenerationContext(String taskId, String outlineNodeId) {
        taskService.require(taskId);
        List<WritingMaterialEntity> usable = mapper
                .selectList(new LambdaQueryWrapper<WritingMaterialEntity>()
                        .eq(WritingMaterialEntity::getTaskId, taskId)
                        .orderByAsc(WritingMaterialEntity::getCreatedAt))
                .stream()
                .filter(material -> !"禁止".equals(permissionResult(material)))
                .filter(material -> {
                    if (!StringUtils.hasText(material.getOutlineNodeId())) return true;
                    return StringUtils.hasText(outlineNodeId) && outlineNodeId.equals(material.getOutlineNodeId());
                })
                .toList();
        if (usable.isEmpty()) return new GenerationContext(0, List.of(), "未提供可用材料，禁止补造事实依据。");
        int snapshotVersion = createSnapshot(taskId).snapshotVersion();
        StringBuilder context =
                new StringBuilder("以下材料已经权限校验并固化为快照 V").append(snapshotVersion).append("：\n");
        List<String> ids = new java.util.ArrayList<>();
        for (WritingMaterialEntity material : usable) {
            ids.add(material.getId());
            String fragment = StringUtils.hasText(material.getSelectedFragment())
                    ? material.getSelectedFragment().trim()
                    : "（未提取文本片段）";
            int remaining = 30_000 - context.length();
            if (remaining <= 0) break;
            String scope = StringUtils.hasText(material.getOutlineNodeId()) ? "本节" : "通篇";
            String safeFragment = fragment.substring(0, Math.min(fragment.length(), Math.min(8_000, remaining)));
            context.append("[材料ID:")
                    .append(material.getId())
                    .append("][")
                    .append(scope)
                    .append("] ")
                    .append(material.getMaterialName())
                    .append("（")
                    .append(material.getAuthorityLevel())
                    .append("）：")
                    .append(safeFragment)
                    .append("\n");
        }
        return new GenerationContext(snapshotVersion, List.copyOf(ids), context.toString());
    }

    public WritingMaterialEntity requireUsable(String taskId, String materialId) {
        taskService.require(taskId);
        WritingMaterialEntity material = requireOwned(taskId, materialId);
        if ("禁止".equals(material.getPermissionStatus())) throw BusinessException.unprocessable("该材料禁止用于生成或引用");
        if (material.getExpiryDate() != null && material.getExpiryDate().isBefore(java.time.LocalDate.now())) {
            throw BusinessException.unprocessable("该材料已失效，不能建立新引用");
        }
        return material;
    }

    @Transactional
    public void delete(String taskId, String materialId) {
        taskService.require(taskId);
        WritingMaterialEntity material = requireOwned(taskId, materialId);
        if (Boolean.TRUE.equals(material.getIsLocked())) throw BusinessException.conflict("材料已锁定，不能移除");
        mapper.deleteById(materialId);
    }

    private WritingMaterialEntity requireOwned(String taskId, String materialId) {
        WritingMaterialEntity material = mapper.selectById(materialId);
        if (material == null || !taskId.equals(material.getTaskId())) throw BusinessException.notFound("材料不存在");
        return material;
    }

    private void validateDates(java.time.LocalDate effective, java.time.LocalDate expiry) {
        if (effective != null && expiry != null && expiry.isBefore(effective))
            throw BusinessException.unprocessable("材料失效日期不得早于生效日期");
    }

    private String permissionResult(WritingMaterialEntity material) {
        return "知识库参考".equals(material.getSourceType()) || "仅供参考".equals(material.getPermissionStatus())
                        || "禁止".equals(material.getPermissionStatus())
                        || (material.getExpiryDate() != null
                                && material.getExpiryDate().isBefore(java.time.LocalDate.now()))
                ? "禁止"
                : material.getPermissionStatus();
    }

    public record GenerationContext(int snapshotVersion, List<String> materialIds, String text) {}
}
