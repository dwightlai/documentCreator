package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.writing.api.DocumentTypeConfigDtos;
import cn.gov.document.platform.writing.domain.DocumentTypeConfigEntity;
import cn.gov.document.platform.writing.domain.DocumentTypeConfigVersionEntity;
import cn.gov.document.platform.writing.persistence.DocumentTypeConfigMapper;
import cn.gov.document.platform.writing.persistence.DocumentTypeConfigVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DocumentTypeConfigService {
    private final DocumentTypeConfigMapper mapper;
    private final DocumentTypeConfigVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public DocumentTypeConfigService(DocumentTypeConfigMapper mapper, DocumentTypeConfigVersionMapper versionMapper,
                                     ObjectMapper objectMapper) {
        this.mapper = mapper; this.versionMapper = versionMapper; this.objectMapper = objectMapper;
    }

    public List<DocumentTypeConfigDtos.Response> list(String category, String direction, String status) {
        List<DocumentTypeConfigDtos.Response> tenantConfigs = mapper.selectList(new LambdaQueryWrapper<DocumentTypeConfigEntity>()
                .eq(StringUtils.hasText(category), DocumentTypeConfigEntity::getDocumentCategory, category)
                .eq(StringUtils.hasText(direction), DocumentTypeConfigEntity::getDirection, direction)
                .eq(StringUtils.hasText(status), DocumentTypeConfigEntity::getLifecycleStatus, status)
                .orderByAsc(DocumentTypeConfigEntity::getTypeCode)).stream().map(this::response).toList();
        var overridden = tenantConfigs.stream().map(DocumentTypeConfigDtos.Response::typeCode).collect(java.util.stream.Collectors.toSet());
        var defaults = BuiltInDocumentTypes.list().stream()
                .filter(value -> !overridden.contains(value.typeCode()))
                .filter(value -> !StringUtils.hasText(category) || category.equals(value.documentCategory()))
                .filter(value -> !StringUtils.hasText(direction) || direction.equals(value.direction()))
                .filter(value -> !StringUtils.hasText(status) || status.equals(value.lifecycleStatus()))
                .toList();
        return java.util.stream.Stream.concat(tenantConfigs.stream(), defaults.stream())
                .sorted(java.util.Comparator.comparing(DocumentTypeConfigDtos.Response::typeCode)).toList();
    }

    public DocumentTypeConfigDtos.Response get(String typeCode) {
        DocumentTypeConfigEntity entity = mapper.selectOne(new LambdaQueryWrapper<DocumentTypeConfigEntity>()
                .eq(DocumentTypeConfigEntity::getTypeCode, typeCode));
        return entity == null ? BuiltInDocumentTypes.find(typeCode).orElseThrow(() -> BusinessException.notFound("文种配置不存在")) : response(entity);
    }

    public Optional<RuntimeConfig> effective(String typeCode) {
        DocumentTypeConfigEntity entity = mapper.selectOne(new LambdaQueryWrapper<DocumentTypeConfigEntity>()
                .eq(DocumentTypeConfigEntity::getTypeCode, typeCode)
                .eq(DocumentTypeConfigEntity::getLifecycleStatus, "生效"));
        return entity == null ? BuiltInDocumentTypes.effective(typeCode) : Optional.of(new RuntimeConfig(read(entity.getRequiredFields()),
                read(entity.getRecommendedStructure()), read(entity.getForbiddenPatterns()), read(entity.getClosingPatterns())));
    }

    @Transactional
    public DocumentTypeConfigDtos.Response create(DocumentTypeConfigDtos.CreateRequest request) {
        if (mapper.selectCount(new LambdaQueryWrapper<DocumentTypeConfigEntity>()
                .eq(DocumentTypeConfigEntity::getTypeCode, request.typeCode().trim())) > 0) {
            throw BusinessException.conflict("文种配置代码已存在");
        }
        DocumentTypeConfigEntity entity = new DocumentTypeConfigEntity();
        entity.setTenantId(RequestContext.tenantId()); entity.setTypeCode(request.typeCode().trim());
        entity.setCurrentVersion(1); entity.setLifecycleStatus("草稿"); apply(entity, request.typeName(), request.documentCategory(),
                request.applicableScenario(), request.direction(), request.requiredFields(), request.recommendedStructure(),
                request.forbiddenPatterns(), request.closingPatterns());
        mapper.insert(entity); insertVersion(entity, request.changeDescription() == null ? "创建文种配置" : request.changeDescription());
        return response(entity);
    }

    @Transactional
    public DocumentTypeConfigDtos.Response update(String typeCode, DocumentTypeConfigDtos.UpdateRequest request) {
        DocumentTypeConfigEntity entity = require(typeCode);
        if (!List.of("草稿", "测试").contains(entity.getLifecycleStatus())) {
            throw BusinessException.conflict("只有草稿或测试状态的文种配置可以编辑");
        }
        entity.setLockVersion(request.lockVersion());
        entity.setCurrentVersion(entity.getCurrentVersion() + 1); entity.setLifecycleStatus("草稿");
        apply(entity, request.typeName(), request.documentCategory(), request.applicableScenario(), request.direction(),
                request.requiredFields(), request.recommendedStructure(), request.forbiddenPatterns(), request.closingPatterns());
        if (mapper.updateById(entity) != 1) throw BusinessException.conflict("文种配置已被其他用户修改");
        insertVersion(entity, request.changeDescription()); return get(typeCode);
    }

    @Transactional
    public DocumentTypeConfigDtos.TestResponse test(String typeCode) {
        DocumentTypeConfigEntity entity = require(typeCode);
        if (!List.of("草稿", "测试").contains(entity.getLifecycleStatus())) throw BusinessException.conflict("当前状态不允许测试");
        List<String> errors = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        if (read(entity.getRequiredFields()).isEmpty()) errors.add("必填要素不能为空");
        if (read(entity.getRecommendedStructure()).isEmpty()) errors.add("推荐结构不能为空");
        if ("请示".equals(entity.getTypeCode()) && read(entity.getClosingPatterns()).isEmpty()) errors.add("请示必须配置规范结束语");
        if (!StringUtils.hasText(entity.getDirection())) warnings.add("未配置行文方向，L3 行文方向审校将降级");
        DocumentTypeConfigDtos.TestResponse result = new DocumentTypeConfigDtos.TestResponse(errors.isEmpty(), errors, warnings);
        DocumentTypeConfigVersionEntity version = requireVersion(entity.getId(), entity.getCurrentVersion());
        version.setTestResults(writeValue(result)); versionMapper.updateById(version);
        if (result.passed()) { entity.setLifecycleStatus("测试"); mapper.updateById(entity); }
        return result;
    }

    @Transactional public DocumentTypeConfigDtos.Response submit(String typeCode) { DocumentTypeConfigEntity e=require(typeCode);
        if(!"测试".equals(e.getLifecycleStatus()))throw BusinessException.conflict("文种配置必须通过测试后才能提交审核");e.setLifecycleStatus("审核");mapper.updateById(e);return get(typeCode); }
    @Transactional public DocumentTypeConfigDtos.Response publish(String typeCode) { DocumentTypeConfigEntity e=require(typeCode);
        if(!"审核".equals(e.getLifecycleStatus()))throw BusinessException.conflict("只有审核状态的文种配置可以发布");e.setLifecycleStatus("生效");mapper.updateById(e);
        DocumentTypeConfigVersionEntity v=requireVersion(e.getId(),e.getCurrentVersion());v.setApprovedBy(RequestContext.userId());v.setApprovedAt(OffsetDateTime.now());versionMapper.updateById(v);return get(typeCode); }
    @Transactional public DocumentTypeConfigDtos.Response deactivate(String typeCode) { DocumentTypeConfigEntity e=require(typeCode);
        if(!"生效".equals(e.getLifecycleStatus()))throw BusinessException.conflict("只有生效文种配置可以停用");e.setLifecycleStatus("停用");mapper.updateById(e);return get(typeCode); }

    public List<DocumentTypeConfigDtos.VersionResponse> versions(String typeCode) { DocumentTypeConfigEntity e=require(typeCode);
        return versionMapper.selectList(new LambdaQueryWrapper<DocumentTypeConfigVersionEntity>().eq(DocumentTypeConfigVersionEntity::getConfigId,e.getId())
                .orderByDesc(DocumentTypeConfigVersionEntity::getVersionNumber)).stream().map(DocumentTypeConfigDtos.VersionResponse::from).toList(); }

    private void apply(DocumentTypeConfigEntity e,String name,String category,String scenario,String direction,List<String> required,
                       List<String> structure,List<String> forbidden,List<String> closing) {
        e.setTypeName(name.trim());e.setDocumentCategory(category);e.setApplicableScenario(scenario);e.setDirection(direction);
        e.setRequiredFields(write(required));e.setRecommendedStructure(write(structure));
        e.setForbiddenPatterns(write(forbidden==null?List.of():forbidden));e.setClosingPatterns(write(closing==null?List.of():closing));
    }
    private void insertVersion(DocumentTypeConfigEntity e,String description) { DocumentTypeConfigVersionEntity v=new DocumentTypeConfigVersionEntity();
        v.setTenantId(RequestContext.tenantId());v.setConfigId(e.getId());v.setVersionNumber(e.getCurrentVersion());v.setFullConfig(fullConfig(e));v.setChangeDescription(description);versionMapper.insert(v); }
    private String fullConfig(DocumentTypeConfigEntity e) { Map<String,Object> value=new LinkedHashMap<>();value.put("typeCode",e.getTypeCode());value.put("typeName",e.getTypeName());
        value.put("documentCategory",e.getDocumentCategory());value.put("applicableScenario",e.getApplicableScenario());value.put("direction",e.getDirection());
        value.put("requiredFields",read(e.getRequiredFields()));value.put("recommendedStructure",read(e.getRecommendedStructure()));
        value.put("forbiddenPatterns",read(e.getForbiddenPatterns()));value.put("closingPatterns",read(e.getClosingPatterns()));return writeValue(value); }
    private DocumentTypeConfigEntity require(String code) { DocumentTypeConfigEntity e=mapper.selectOne(new LambdaQueryWrapper<DocumentTypeConfigEntity>().eq(DocumentTypeConfigEntity::getTypeCode,code));
        if(e==null)throw BusinessException.notFound("文种配置不存在");return e; }
    private DocumentTypeConfigVersionEntity requireVersion(String id,int version) { DocumentTypeConfigVersionEntity e=versionMapper.selectOne(new LambdaQueryWrapper<DocumentTypeConfigVersionEntity>()
        .eq(DocumentTypeConfigVersionEntity::getConfigId,id).eq(DocumentTypeConfigVersionEntity::getVersionNumber,version));if(e==null)throw BusinessException.notFound("文种配置版本不存在");return e; }
    private DocumentTypeConfigDtos.Response response(DocumentTypeConfigEntity e) { return DocumentTypeConfigDtos.Response.from(e,read(e.getRequiredFields()),read(e.getRecommendedStructure()),read(e.getForbiddenPatterns()),read(e.getClosingPatterns())); }
    private String write(List<String> value) { return writeValue(value==null?List.of():value); }
    private String writeValue(Object value) { try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);} }
    private List<String> read(String value) { try{return value==null?List.of():objectMapper.readValue(value,new TypeReference<>(){});}catch(JsonProcessingException ex){throw new IllegalStateException("文种配置数组数据损坏",ex);} }

    public record RuntimeConfig(List<String> requiredFields,List<String> recommendedStructure,List<String> forbiddenPatterns,List<String> closingPatterns) { }
}
