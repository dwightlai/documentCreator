package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.api.PageResponse;
import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.port.OutputTemplatePort;
import cn.gov.document.platform.writing.api.WritingTaskDtos.CreateRequest;
import cn.gov.document.platform.writing.api.WritingTaskDtos.Response;
import cn.gov.document.platform.writing.api.WritingTaskDtos.StatusRequest;
import cn.gov.document.platform.writing.api.WritingTaskDtos.UpdateRequest;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import cn.gov.document.platform.writing.domain.WritingTaskStatus;
import cn.gov.document.platform.writing.persistence.WritingTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WritingTaskService {
    private final WritingTaskMapper mapper;
    private final ObjectProvider<OutputTemplatePort> templatePort;

    public WritingTaskService(WritingTaskMapper mapper, ObjectProvider<OutputTemplatePort> templatePort) {
        this.mapper = mapper;
        this.templatePort = templatePort;
    }

    @Transactional
    public Response create(CreateRequest request) {
        WritingTaskEntity entity = new WritingTaskEntity();
        entity.setTenantId(RequestContext.tenantId());
        apply(
                entity,
                request.taskName(),
                request.documentTypeCode(),
                request.documentCategory(),
                request.writingTopic(),
                request.writingPurpose(),
                request.direction(),
                request.targetAudience(),
                request.primaryRecipient(),
                request.writingTone(),
                request.expectedLengthMin(),
                request.expectedLengthMax(),
                request.targetDate(),
                request.allowExternalKnowledge(),
                request.securityLevel(),
                resolveTemplate(request.outputTemplateId(), request.documentTypeCode()));
        entity.setStatus(WritingTaskStatus.DRAFT.code());
        mapper.insert(entity);
        return Response.from(entity);
    }

    public Response get(String id) {
        return Response.from(require(id));
    }

    public PageResponse<Response> list(long page, long size, String status, String documentType) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(200, Math.max(1, size));
        LambdaQueryWrapper<WritingTaskEntity> query = new LambdaQueryWrapper<WritingTaskEntity>()
                .eq(StringUtils.hasText(status), WritingTaskEntity::getStatus, status)
                .eq(StringUtils.hasText(documentType), WritingTaskEntity::getDocumentTypeCode, documentType)
                .orderByDesc(WritingTaskEntity::getUpdatedAt);
        Page<WritingTaskEntity> result = mapper.selectPage(Page.of(safePage, safeSize), query);
        return new PageResponse<>(
                result.getRecords().stream().map(Response::from).toList(), safePage, safeSize, result.getTotal());
    }

    @Transactional
    public Response update(String id, UpdateRequest request) {
        WritingTaskEntity entity = require(id);
        entity.setLockVersion(request.lockVersion());
        apply(
                entity,
                request.taskName(),
                request.documentTypeCode(),
                request.documentCategory(),
                request.writingTopic(),
                request.writingPurpose(),
                request.direction(),
                request.targetAudience(),
                request.primaryRecipient(),
                request.writingTone(),
                request.expectedLengthMin(),
                request.expectedLengthMax(),
                request.targetDate(),
                request.allowExternalKnowledge(),
                request.securityLevel(),
                resolveTemplate(request.outputTemplateId(), request.documentTypeCode()));
        if (mapper.updateById(entity) != 1) throw BusinessException.conflict("写作任务已被其他用户修改，请刷新后重试");
        return Response.from(require(id));
    }

    @Transactional
    public Response changeStatus(String id, StatusRequest request) {
        WritingTaskEntity entity = require(id);
        WritingTaskStatus current = WritingTaskStatus.fromCode(entity.getStatus());
        WritingTaskStatus target = WritingTaskStatus.fromCode(request.status());
        if (!current.canTransitionTo(target)) {
            throw BusinessException.unprocessable("不允许从“" + current.code() + "”转为“" + target.code() + "”");
        }
        if (target == WritingTaskStatus.CLOSED && !StringUtils.hasText(request.reason())) {
            throw BusinessException.unprocessable("关闭任务必须填写原因");
        }
        entity.setLockVersion(request.lockVersion());
        entity.setStatus(target.code());
        if (mapper.updateById(entity) != 1) throw BusinessException.conflict("任务状态已变化，请刷新后重试");
        return Response.from(require(id));
    }

    @Transactional
    public void delete(String id) {
        require(id);
        mapper.deleteById(id);
    }

    public WritingTaskEntity require(String id) {
        WritingTaskEntity entity = mapper.selectById(id);
        if (entity == null) throw BusinessException.notFound("写作任务不存在");
        return entity;
    }

    /** 关联外部业务系统（§31.1 从 OA 发起写作）。 */
    @Transactional
    public Response linkExternal(
            String taskId, String sourceSystem, String businessId, String draftingDepartment, String callbackUrl) {
        WritingTaskEntity task = require(taskId);
        task.setSourceSystem(sourceSystem);
        task.setBusinessId(businessId);
        task.setDraftingDepartment(draftingDepartment);
        task.setCallbackUrl(callbackUrl);
        mapper.updateById(task);
        return Response.from(require(taskId));
    }

    @Transactional
    public void attachCurrentDocument(String taskId, String documentId) {
        WritingTaskEntity task = require(taskId);
        task.setCurrentDocumentId(documentId);
        if (WritingTaskStatus.GENERATING.code().equals(task.getStatus())) {
            task.setStatus(WritingTaskStatus.EDITING.code());
        }
        mapper.updateById(task);
    }

    private void apply(
            WritingTaskEntity entity,
            String name,
            String typeCode,
            String category,
            String topic,
            String purpose,
            String direction,
            String audience,
            String recipient,
            String tone,
            Integer min,
            Integer max,
            java.time.LocalDate date,
            Boolean external,
            String security,
            String templateId) {
        entity.setTaskName(name.trim());
        entity.setDocumentTypeCode(typeCode.trim());
        entity.setDocumentCategory(category);
        entity.setWritingTopic(topic.trim());
        entity.setWritingPurpose(purpose);
        entity.setDirection(direction);
        entity.setTargetAudience(audience);
        entity.setPrimaryRecipient(recipient);
        entity.setWritingTone(tone);
        entity.setExpectedLengthMin(min);
        entity.setExpectedLengthMax(max);
        entity.setTargetDate(date);
        entity.setAllowExternalKnowledge(Boolean.TRUE.equals(external));
        entity.setSecurityLevel(StringUtils.hasText(security) ? security : "普通");
        entity.setOutputTemplateId(templateId);
    }

    private String resolveTemplate(String requestedTemplateId, String documentTypeCode) {
        OutputTemplatePort port = templatePort.getIfAvailable();
        if (port == null) {
            if (StringUtils.hasText(requestedTemplateId)) {
                throw BusinessException.unprocessable("输出模板服务不可用");
            }
            return null;
        }
        return port.resolveEffectiveTemplate(requestedTemplateId, documentTypeCode.trim());
    }
}
