package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.writing.api.WritingPlanningDtos;
import cn.gov.document.platform.writing.domain.WritingIdeaEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import cn.gov.document.platform.writing.persistence.WritingIdeaMapper;
import cn.gov.document.platform.writing.persistence.WritingMaterialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WritingPlanningService {
    private final WritingIdeaMapper ideaMapper;
    private final WritingMaterialMapper materialMapper;
    private final WritingTaskService taskService;
    private final WritingAssistant assistant;
    private final ObjectMapper objectMapper;

    public WritingPlanningService(
            WritingIdeaMapper ideaMapper,
            WritingMaterialMapper materialMapper,
            WritingTaskService taskService,
            WritingAssistant assistant,
            ObjectMapper objectMapper) {
        this.ideaMapper = ideaMapper;
        this.materialMapper = materialMapper;
        this.taskService = taskService;
        this.assistant = assistant;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WritingPlanningDtos.IdeaResponse generateIdea(String taskId) {
        WritingTaskEntity task = taskService.require(taskId);
        long materialCount = materialMapper.selectCount(
                new LambdaQueryWrapper<cn.gov.document.platform.writing.domain.WritingMaterialEntity>()
                        .eq(cn.gov.document.platform.writing.domain.WritingMaterialEntity::getTaskId, taskId));
        int version = ideaMapper
                .selectList(new LambdaQueryWrapper<WritingIdeaEntity>()
                        .eq(WritingIdeaEntity::getTaskId, taskId)
                        .orderByDesc(WritingIdeaEntity::getVersion)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(item -> item.getVersion() + 1)
                .orElse(1);
        WritingAssistant.IdeaDraft draft = assistant.generateIdea(task, Math.toIntExact(materialCount));
        WritingIdeaEntity entity = new WritingIdeaEntity();
        entity.setTenantId(RequestContext.tenantId());
        entity.setTaskId(taskId);
        entity.setVersion(version);
        entity.setMainTheme(draft.mainTheme());
        entity.setCoreViewpoints(write(draft.coreViewpoints()));
        entity.setWritingSequence(write(draft.writingSequence()));
        entity.setMustExpressFacts(draft.mustExpressFacts());
        entity.setOptionalContent(draft.optionalContent());
        entity.setMissingMaterials(draft.missingMaterials());
        entity.setRiskWarnings(draft.riskWarnings());
        entity.setIsConfirmed(false);
        ideaMapper.insert(entity);
        return response(entity);
    }

    public WritingPlanningDtos.IdeaResponse getIdea(String taskId, int version) {
        taskService.require(taskId);
        return response(require(taskId, version));
    }

    @Transactional
    public WritingPlanningDtos.IdeaResponse updateIdea(
            String taskId, int version, WritingPlanningDtos.UpdateIdeaRequest request) {
        taskService.require(taskId);
        WritingIdeaEntity entity = require(taskId, version);
        if (Boolean.TRUE.equals(entity.getIsConfirmed())) throw BusinessException.conflict("已确认的写作思路不可修改，请生成新版本");
        entity.setLockVersion(request.lockVersion());
        entity.setMainTheme(request.mainTheme().trim());
        entity.setCoreViewpoints(write(request.coreViewpoints()));
        entity.setWritingSequence(write(request.writingSequence()));
        entity.setMustExpressFacts(request.mustExpressFacts());
        entity.setOptionalContent(request.optionalContent());
        entity.setMissingMaterials(request.missingMaterials());
        entity.setRiskWarnings(request.riskWarnings());
        entity.setIsConfirmed(request.confirmed());
        if (request.confirmed()) {
            entity.setConfirmedBy(RequestContext.userId());
            entity.setConfirmedAt(OffsetDateTime.now());
        }
        if (ideaMapper.updateById(entity) != 1) throw BusinessException.conflict("写作思路已被其他用户修改");
        return response(require(taskId, version));
    }

    public List<WritingPlanningDtos.TitleCandidate> titleCandidates(String taskId) {
        return assistant.generateTitleCandidates(taskService.require(taskId));
    }

    private WritingIdeaEntity require(String taskId, int version) {
        WritingIdeaEntity entity = ideaMapper.selectOne(new LambdaQueryWrapper<WritingIdeaEntity>()
                .eq(WritingIdeaEntity::getTaskId, taskId)
                .eq(WritingIdeaEntity::getVersion, version));
        if (entity == null) throw BusinessException.notFound("写作思路版本不存在");
        return entity;
    }

    private WritingPlanningDtos.IdeaResponse response(WritingIdeaEntity entity) {
        return WritingPlanningDtos.IdeaResponse.from(
                entity, read(entity.getCoreViewpoints()), read(entity.getWritingSequence()));
    }

    private String write(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> read(String value) {
        try {
            return value == null ? List.of() : objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("写作思路数组数据损坏", exception);
        }
    }
}
