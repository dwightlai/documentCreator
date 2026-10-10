package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.port.OutputTemplatePort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OutputTemplateService implements OutputTemplatePort {
    private static final Map<String, String> PLACEHOLDER_DESCRIPTIONS = Map.ofEntries(
            Map.entry("{title}", "公文标题"),
            Map.entry("{recipient}", "主送机关"),
            Map.entry("{sender}", "发文机关标志"),
            Map.entry("{doc_number}", "发文字号"),
            Map.entry("{signer}", "签发人"),
            Map.entry("{content}", "正文内容"),
            Map.entry("{closing}", "结束语"),
            Map.entry("{attachment_desc}", "附件说明"),
            Map.entry("{copy_to}", "抄送机关"),
            Map.entry("{issue_unit}", "印发机关"),
            Map.entry("{issue_date}", "印发日期"),
            Map.entry("{date}", "成文日期"),
            Map.entry("{#sections}", "正文循环开始"),
            Map.entry("{paragraph}", "循环段落"),
            Map.entry("{/sections}", "正文循环结束"),
            Map.entry("{#has_attachment}", "附件条件开始"),
            Map.entry("{/has_attachment}", "附件条件结束"));

    private final OutputTemplateMapper mapper;
    private final OutputTemplateVersionMapper versionMapper;
    private final DocxTemplateEngine engine;
    private final TemplateFileStore fileStore;
    private final ObjectMapper objectMapper;

    public OutputTemplateService(
            OutputTemplateMapper mapper,
            OutputTemplateVersionMapper versionMapper,
            DocxTemplateEngine engine,
            TemplateFileStore fileStore,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.versionMapper = versionMapper;
        this.engine = engine;
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
    }

    public List<OutputTemplateDtos.Response> list(String documentTypeCode, String status) {
        return mapper
                .selectList(new LambdaQueryWrapper<OutputTemplateEntity>()
                        .eq(
                                StringUtils.hasText(documentTypeCode),
                                OutputTemplateEntity::getDocumentTypeCode,
                                documentTypeCode)
                        .eq(StringUtils.hasText(status), OutputTemplateEntity::getLifecycleStatus, status)
                        .orderByAsc(OutputTemplateEntity::getDocumentTypeCode)
                        .orderByAsc(OutputTemplateEntity::getTemplateCode))
                .stream()
                .map(this::response)
                .toList();
    }

    public OutputTemplateDtos.Response get(String id) {
        return response(require(id));
    }

    public List<OutputTemplateDtos.Response> effectiveByDocumentType(String documentTypeCode) {
        return list(documentTypeCode, "生效");
    }

    @Override
    public String resolveEffectiveTemplate(String requestedTemplateId, String documentTypeCode) {
        if (StringUtils.hasText(requestedTemplateId)) {
            OutputTemplateEntity selected = require(requestedTemplateId);
            if (!"生效".equals(selected.getLifecycleStatus())) {
                throw BusinessException.unprocessable("所选输出模板尚未发布生效");
            }
            if (!selected.getDocumentTypeCode().equals(documentTypeCode)) {
                throw BusinessException.unprocessable("所选输出模板与写作文种不匹配");
            }
            return selected.getId();
        }
        return mapper
                .selectList(new LambdaQueryWrapper<OutputTemplateEntity>()
                        .eq(OutputTemplateEntity::getDocumentTypeCode, documentTypeCode)
                        .eq(OutputTemplateEntity::getLifecycleStatus, "生效")
                        .orderByAsc(OutputTemplateEntity::getTemplateCode)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(OutputTemplateEntity::getId)
                .orElse(null);
    }

    @Transactional
    public OutputTemplateDtos.Response create(
            OutputTemplateDtos.CreateMetadata metadata, String originalFileName, byte[] bytes) {
        if (mapper.selectCount(new LambdaQueryWrapper<OutputTemplateEntity>()
                        .eq(
                                OutputTemplateEntity::getTemplateCode,
                                metadata.templateCode().trim()))
                > 0) {
            throw BusinessException.conflict("输出模板代码已存在");
        }
        DocxTemplateEngine.Inspection inspection = inspectValid(originalFileName, bytes);
        OutputTemplateEntity entity = new OutputTemplateEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTenantId(RequestContext.tenantId());
        entity.setTemplateCode(metadata.templateCode().trim());
        applyMetadata(
                entity,
                metadata.templateName(),
                metadata.documentTypeCode(),
                metadata.hasRedHeader(),
                metadata.hasFooterMark(),
                metadata.direction(),
                metadata.description());
        entity.setCurrentVersion(1);
        entity.setLifecycleStatus("草稿");
        entity.setTemplateFileHash(inspection.fileHash());
        entity.setPlaceholderMap(writePlaceholderMap(inspection.placeholders()));
        entity.setTemplateFilePath(fileStore.write(entity.getId(), 1, bytes));
        mapper.insert(entity);
        insertVersion(entity, "初始上传", "草稿", null, null);
        return response(entity);
    }

    @Transactional
    public OutputTemplateDtos.Response update(String id, OutputTemplateDtos.UpdateMetadata request) {
        OutputTemplateEntity entity = require(id);
        entity.setLockVersion(request.lockVersion());
        applyMetadata(
                entity,
                request.templateName(),
                request.documentTypeCode(),
                request.hasRedHeader(),
                request.hasFooterMark(),
                request.direction(),
                request.description());
        if (mapper.updateById(entity) != 1) {
            throw BusinessException.conflict("输出模板已被其他用户修改");
        }
        return response(require(id));
    }

    @Transactional
    public OutputTemplateDtos.Response replaceFile(
            String id, String originalFileName, byte[] bytes, String changeNote) {
        OutputTemplateEntity entity = require(id);
        DocxTemplateEngine.Inspection inspection = inspectValid(originalFileName, bytes);
        int version = entity.getCurrentVersion() + 1;
        entity.setCurrentVersion(version);
        entity.setTemplateFilePath(fileStore.write(id, version, bytes));
        entity.setTemplateFileHash(inspection.fileHash());
        entity.setPlaceholderMap(writePlaceholderMap(inspection.placeholders()));
        entity.setLifecycleStatus("草稿");
        mapper.updateById(entity);
        insertVersion(entity, changeNote, "草稿", null, null);
        return response(entity);
    }

    @Transactional
    public DocxTemplateEngine.Inspection parse(String id) {
        OutputTemplateEntity entity = require(id);
        DocxTemplateEngine.Inspection inspection =
                engine.inspect("template.docx", fileStore.read(entity.getTemplateFilePath()));
        entity.setPlaceholderMap(writePlaceholderMap(inspection.placeholders()));
        mapper.updateById(entity);
        return inspection;
    }

    @Transactional
    public byte[] test(String id, OutputTemplateDtos.RenderRequest request) {
        OutputTemplateEntity entity = require(id);
        byte[] rendered = engine.render(
                fileStore.read(entity.getTemplateFilePath()),
                request.values() == null ? Map.of() : request.values(),
                request.sections() == null ? List.of() : request.sections());
        entity.setLifecycleStatus("测试");
        mapper.updateById(entity);
        OutputTemplateVersionEntity version = currentVersion(id, entity.getCurrentVersion());
        version.setLifecycleStatus("测试");
        versionMapper.updateById(version);
        return rendered;
    }

    @Transactional
    public OutputTemplateDtos.Response publish(String id) {
        OutputTemplateEntity entity = require(id);
        if (!"测试".equals(entity.getLifecycleStatus())) {
            throw BusinessException.unprocessable("输出模板必须先通过模拟填充测试才能发布");
        }
        DocxTemplateEngine.Inspection inspection =
                inspectValid("template.docx", fileStore.read(entity.getTemplateFilePath()));
        entity.setTemplateFileHash(inspection.fileHash());
        entity.setLifecycleStatus("生效");
        mapper.updateById(entity);
        OutputTemplateVersionEntity version = currentVersion(id, entity.getCurrentVersion());
        version.setLifecycleStatus("生效");
        version.setApprovedBy(RequestContext.userId());
        version.setApprovedAt(OffsetDateTime.now());
        versionMapper.updateById(version);
        return response(entity);
    }

    public List<OutputTemplateDtos.VersionResponse> versions(String id) {
        require(id);
        return versionMapper
                .selectList(new LambdaQueryWrapper<OutputTemplateVersionEntity>()
                        .eq(OutputTemplateVersionEntity::getTemplateId, id)
                        .orderByDesc(OutputTemplateVersionEntity::getVersionNumber))
                .stream()
                .map(this::versionResponse)
                .toList();
    }

    public byte[] render(String id, OutputTemplateDtos.RenderRequest request) {
        OutputTemplateEntity entity = require(id);
        return engine.render(
                fileStore.read(entity.getTemplateFilePath()),
                request.values() == null ? Map.of() : request.values(),
                request.sections() == null ? List.of() : request.sections());
    }

    public byte[] fileContent(String id) {
        OutputTemplateEntity entity = require(id);
        return fileStore.read(entity.getTemplateFilePath());
    }

    private DocxTemplateEngine.Inspection inspectValid(String fileName, byte[] bytes) {
        DocxTemplateEngine.Inspection inspection = engine.inspect(fileName, bytes);
        if (!inspection.errors().isEmpty()) {
            throw BusinessException.unprocessable(String.join("；", inspection.errors()));
        }
        return inspection;
    }

    private OutputTemplateEntity require(String id) {
        OutputTemplateEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("输出模板不存在");
        }
        return entity;
    }

    private void applyMetadata(
            OutputTemplateEntity entity,
            String name,
            String type,
            boolean redHeader,
            boolean footerMark,
            String direction,
            String description) {
        entity.setTemplateName(name.trim());
        entity.setDocumentTypeCode(type.trim());
        entity.setHasRedHeader(redHeader);
        entity.setHasFooterMark(footerMark);
        entity.setDirection(direction);
        entity.setDescription(description);
    }

    private void insertVersion(
            OutputTemplateEntity entity, String note, String status, String approvedBy, OffsetDateTime approvedAt) {
        OutputTemplateVersionEntity version = new OutputTemplateVersionEntity();
        version.setTenantId(RequestContext.tenantId());
        version.setTemplateId(entity.getId());
        version.setVersionNumber(entity.getCurrentVersion());
        version.setTemplateFilePath(entity.getTemplateFilePath());
        version.setTemplateFileHash(entity.getTemplateFileHash());
        version.setChangeNote(StringUtils.hasText(note) ? note : "替换模板文件");
        version.setLifecycleStatus(status);
        version.setApprovedBy(approvedBy);
        version.setApprovedAt(approvedAt);
        versionMapper.insert(version);
    }

    private OutputTemplateVersionEntity currentVersion(String id, int number) {
        OutputTemplateVersionEntity version =
                versionMapper.selectOne(new LambdaQueryWrapper<OutputTemplateVersionEntity>()
                        .eq(OutputTemplateVersionEntity::getTemplateId, id)
                        .eq(OutputTemplateVersionEntity::getVersionNumber, number));
        if (version == null) {
            throw BusinessException.conflict("输出模板版本记录不存在");
        }
        return version;
    }

    private String writePlaceholderMap(List<String> placeholders) {
        Map<String, String> map = new LinkedHashMap<>();
        placeholders.forEach(value -> map.put(value, PLACEHOLDER_DESCRIPTIONS.getOrDefault(value, "自定义占位符")));
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("占位符映射序列化失败", impossible);
        }
    }

    private Map<String, String> readPlaceholderMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (JsonProcessingException invalidStoredValue) {
            throw new IllegalStateException("占位符映射数据损坏", invalidStoredValue);
        }
    }

    private OutputTemplateDtos.Response response(OutputTemplateEntity entity) {
        return new OutputTemplateDtos.Response(
                entity.getId(),
                entity.getTemplateCode(),
                entity.getTemplateName(),
                entity.getDocumentTypeCode(),
                entity.getTemplateFileHash(),
                Boolean.TRUE.equals(entity.getHasRedHeader()),
                Boolean.TRUE.equals(entity.getHasFooterMark()),
                readPlaceholderMap(entity.getPlaceholderMap()),
                entity.getDirection(),
                entity.getDescription(),
                entity.getCurrentVersion(),
                entity.getLifecycleStatus(),
                entity.getLockVersion(),
                entity.getUpdatedAt());
    }

    private OutputTemplateDtos.VersionResponse versionResponse(OutputTemplateVersionEntity entity) {
        return new OutputTemplateDtos.VersionResponse(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getTemplateFileHash(),
                entity.getChangeNote(),
                entity.getLifecycleStatus(),
                entity.getApprovedBy(),
                entity.getApprovedAt(),
                entity.getCreatedAt());
    }
}
