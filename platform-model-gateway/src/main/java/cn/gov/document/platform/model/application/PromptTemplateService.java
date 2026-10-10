package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.model.api.PromptTemplateDtos;
import cn.gov.document.platform.model.domain.*;
import cn.gov.document.platform.model.persistence.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PromptTemplateService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_.-]{0,99})}}");
    private final PromptTemplateMapper mapper;
    private final PromptTemplateVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public PromptTemplateService(
            PromptTemplateMapper mapper, PromptTemplateVersionMapper versionMapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    public List<PromptTemplateDtos.Response> list(String scene, String documentTypeCode, String status) {
        return mapper
                .selectList(new LambdaQueryWrapper<PromptTemplateEntity>()
                        .eq(StringUtils.hasText(scene), PromptTemplateEntity::getApplicableScene, scene)
                        .eq(
                                StringUtils.hasText(documentTypeCode),
                                PromptTemplateEntity::getDocumentTypeCode,
                                documentTypeCode)
                        .eq(StringUtils.hasText(status), PromptTemplateEntity::getLifecycleStatus, status)
                        .orderByAsc(PromptTemplateEntity::getApplicableScene)
                        .orderByAsc(PromptTemplateEntity::getTemplateCode))
                .stream()
                .map(this::response)
                .toList();
    }

    public PromptTemplateDtos.Response get(String id) {
        return response(require(id));
    }

    @Transactional
    public PromptTemplateDtos.Response create(PromptTemplateDtos.Request r) {
        validate(r);
        if (mapper.selectCount(new LambdaQueryWrapper<PromptTemplateEntity>()
                        .eq(
                                PromptTemplateEntity::getTemplateCode,
                                r.templateCode().trim()))
                > 0) throw BusinessException.conflict("提示词模板代码已存在");
        PromptTemplateEntity e = new PromptTemplateEntity();
        e.setTenantId(RequestContext.tenantId());
        e.setCurrentVersion(1);
        e.setLifecycleStatus("草稿");
        apply(e, r);
        mapper.insert(e);
        insertVersion(e, r.changeDescription() == null ? "创建提示词模板" : r.changeDescription());
        return response(e);
    }

    @Transactional
    public PromptTemplateDtos.Response update(String id, PromptTemplateDtos.Request r) {
        validate(r);
        PromptTemplateEntity e = require(id);
        if (!List.of("草稿", "测试").contains(e.getLifecycleStatus()))
            throw BusinessException.conflict("只有草稿或测试状态的提示词模板可以编辑");
        if (r.lockVersion() == null) throw BusinessException.unprocessable("更新提示词模板必须提交 lockVersion");
        PromptTemplateEntity duplicate = mapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTemplateCode, r.templateCode().trim()));
        if (duplicate != null && !duplicate.getId().equals(id)) throw BusinessException.conflict("提示词模板代码已存在");
        e.setLockVersion(r.lockVersion());
        e.setCurrentVersion(e.getCurrentVersion() + 1);
        e.setLifecycleStatus("草稿");
        apply(e, r);
        if (mapper.updateById(e) != 1) throw BusinessException.conflict("提示词模板已被其他用户修改");
        insertVersion(e, r.changeDescription());
        return get(id);
    }

    @Transactional
    public PromptTemplateDtos.TestResponse test(String id, PromptTemplateDtos.TestRequest r) {
        PromptTemplateEntity e = require(id);
        if (!List.of("草稿", "测试").contains(e.getLifecycleStatus())) throw BusinessException.conflict("当前状态不允许测试");
        String rendered = render(fullPrompt(e), r.variables());
        List<String> unresolved = unresolved(rendered);
        PromptTemplateDtos.TestResponse result =
                new PromptTemplateDtos.TestResponse(unresolved.isEmpty(), rendered, unresolved, sha256(rendered));
        PromptTemplateVersionEntity v = requireVersion(e.getId(), e.getCurrentVersion());
        v.setTestResults(write(Map.of(
                "passed",
                result.passed(),
                "promptHash",
                result.promptHash(),
                "unresolvedPlaceholders",
                result.unresolvedPlaceholders())));
        versionMapper.updateById(v);
        if (result.passed()) {
            e.setLifecycleStatus("测试");
            mapper.updateById(e);
        }
        return result;
    }

    @Transactional
    public PromptTemplateDtos.Response submit(String id) {
        PromptTemplateEntity e = require(id);
        if (!"测试".equals(e.getLifecycleStatus())) throw BusinessException.conflict("提示词模板必须通过测试后才能提交审核");
        e.setLifecycleStatus("审核");
        mapper.updateById(e);
        return get(id);
    }

    @Transactional
    public PromptTemplateDtos.Response publish(String id) {
        PromptTemplateEntity e = require(id);
        if (!"审核".equals(e.getLifecycleStatus())) throw BusinessException.conflict("只有审核状态的提示词模板可以发布");
        e.setLifecycleStatus("生效");
        mapper.updateById(e);
        PromptTemplateVersionEntity v = requireVersion(e.getId(), e.getCurrentVersion());
        v.setApprovedBy(RequestContext.userId());
        v.setApprovedAt(OffsetDateTime.now());
        versionMapper.updateById(v);
        return get(id);
    }

    @Transactional
    public PromptTemplateDtos.Response deactivate(String id) {
        PromptTemplateEntity e = require(id);
        if (!"生效".equals(e.getLifecycleStatus())) throw BusinessException.conflict("只有生效提示词模板可以停用");
        e.setLifecycleStatus("停用");
        mapper.updateById(e);
        return get(id);
    }

    public List<PromptTemplateDtos.VersionResponse> versions(String id) {
        require(id);
        return versionMapper
                .selectList(new LambdaQueryWrapper<PromptTemplateVersionEntity>()
                        .eq(PromptTemplateVersionEntity::getTemplateId, id)
                        .orderByDesc(PromptTemplateVersionEntity::getVersionNumber))
                .stream()
                .map(PromptTemplateDtos.VersionResponse::from)
                .toList();
    }

    public Optional<ResolvedPrompt> resolve(
            String scene, String documentTypeCode, String modelName, Map<String, Object> variables) {
        return mapper
                .selectList(new LambdaQueryWrapper<PromptTemplateEntity>()
                        .eq(PromptTemplateEntity::getApplicableScene, scene)
                        .eq(PromptTemplateEntity::getLifecycleStatus, "生效")
                        .and(StringUtils.hasText(documentTypeCode), q -> q.eq(
                                        PromptTemplateEntity::getDocumentTypeCode, documentTypeCode)
                                .or()
                                .isNull(PromptTemplateEntity::getDocumentTypeCode))
                        .orderByDesc(PromptTemplateEntity::getDocumentTypeCode))
                .stream()
                .filter(e -> readList(e.getApplicableModels()).isEmpty()
                        || readList(e.getApplicableModels()).contains(modelName))
                .findFirst()
                .map(e -> new ResolvedPrompt(
                        e.getId(),
                        e.getCurrentVersion(),
                        render(fullPrompt(e), variables),
                        readMap(e.getModelParameters())));
    }

    private void validate(PromptTemplateDtos.Request r) {
        if (r.inputFields() != null && r.inputFields().size() > 200)
            throw BusinessException.unprocessable("输入字段定义不得超过 200 项");
        if (r.modelParameters() != null) {
            Object temperature = r.modelParameters().get("temperature");
            if (temperature instanceof Number n && (n.doubleValue() < 0 || n.doubleValue() > 2))
                throw BusinessException.unprocessable("temperature 必须在 0 到 2 之间");
        }
    }

    private void apply(PromptTemplateEntity e, PromptTemplateDtos.Request r) {
        e.setTemplateCode(r.templateCode().trim());
        e.setTemplateName(r.templateName().trim());
        e.setApplicableScene(r.applicableScene());
        e.setDocumentTypeCode(r.documentTypeCode());
        e.setApplicableModels(write(r.applicableModels() == null ? List.of() : r.applicableModels()));
        e.setSystemRole(r.systemRole());
        e.setDocumentTypeMethod(r.documentTypeMethod());
        e.setInputFields(write(r.inputFields() == null ? Map.of() : r.inputFields()));
        e.setMaterialCitationRule(r.materialCitationRule());
        e.setAntiFabricationRule(r.antiFabricationRule());
        e.setOutputFormat(r.outputFormat());
        e.setRiskConstraint(r.riskConstraint());
        e.setModelParameters(write(r.modelParameters() == null ? Map.of() : r.modelParameters()));
        e.setTestSampleIds(write(r.testSampleIds() == null ? List.of() : r.testSampleIds()));
    }

    private String fullPrompt(PromptTemplateEntity e) {
        return "[系统角色]\n" + safe(e.getSystemRole()) + "\n[文种方法]\n" + safe(e.getDocumentTypeMethod()) + "\n[材料引用]\n"
                + safe(e.getMaterialCitationRule()) + "\n[禁止编造]\n" + safe(e.getAntiFabricationRule()) + "\n[输出格式]\n"
                + safe(e.getOutputFormat()) + "\n[风险约束]\n" + safe(e.getRiskConstraint());
    }

    private String render(String template, Map<String, Object> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            Object value = values.get(m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(value == null ? m.group() : String.valueOf(value)));
        }
        m.appendTail(b);
        return b.toString();
    }

    private List<String> unresolved(String value) {
        return PLACEHOLDER
                .matcher(value)
                .results()
                .map(r -> r.group(1))
                .distinct()
                .toList();
    }

    private void insertVersion(PromptTemplateEntity e, String description) {
        PromptTemplateVersionEntity v = new PromptTemplateVersionEntity();
        v.setTenantId(RequestContext.tenantId());
        v.setTemplateId(e.getId());
        v.setVersionNumber(e.getCurrentVersion());
        v.setSystemRole(e.getSystemRole());
        v.setFullPrompt(fullPrompt(e));
        v.setChangeDescription(description);
        versionMapper.insert(v);
    }

    private PromptTemplateEntity require(String id) {
        PromptTemplateEntity e = mapper.selectById(id);
        if (e == null) throw BusinessException.notFound("提示词模板不存在");
        return e;
    }

    private PromptTemplateVersionEntity requireVersion(String id, int version) {
        PromptTemplateVersionEntity e = versionMapper.selectOne(new LambdaQueryWrapper<PromptTemplateVersionEntity>()
                .eq(PromptTemplateVersionEntity::getTemplateId, id)
                .eq(PromptTemplateVersionEntity::getVersionNumber, version));
        if (e == null) throw BusinessException.notFound("提示词模板版本不存在");
        return e;
    }

    private PromptTemplateDtos.Response response(PromptTemplateEntity e) {
        return new PromptTemplateDtos.Response(
                e.getId(),
                e.getTemplateCode(),
                e.getTemplateName(),
                e.getApplicableScene(),
                e.getDocumentTypeCode(),
                readList(e.getApplicableModels()),
                e.getSystemRole(),
                e.getDocumentTypeMethod(),
                readMap(e.getInputFields()),
                e.getMaterialCitationRule(),
                e.getAntiFabricationRule(),
                e.getOutputFormat(),
                e.getRiskConstraint(),
                readMap(e.getModelParameters()),
                readList(e.getTestSampleIds()),
                e.getCurrentVersion(),
                e.getLifecycleStatus(),
                e.getLockVersion(),
                e.getUpdatedAt());
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> readList(String v) {
        try {
            return !StringUtils.hasText(v) ? List.of() : objectMapper.readValue(v, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("提示词模板数组数据损坏", e);
        }
    }

    private Map<String, Object> readMap(String v) {
        try {
            return !StringUtils.hasText(v) ? Map.of() : objectMapper.readValue(v, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("提示词模板对象数据损坏", e);
        }
    }

    private String sha256(String v) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record ResolvedPrompt(String templateId, int version, String prompt, Map<String, Object> modelParameters) {}
}
