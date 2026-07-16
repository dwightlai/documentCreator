package cn.gov.document.platform.rule.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.rule.api.RuleDtos;
import cn.gov.document.platform.rule.domain.ReviewRuleEntity;
import cn.gov.document.platform.rule.domain.RuleVersionEntity;
import cn.gov.document.platform.rule.persistence.ReviewRuleMapper;
import cn.gov.document.platform.rule.persistence.RuleVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class RuleService {
    private static final List<String> RISKS=List.of("提示","一般","重要","严重");
    private final ReviewRuleMapper ruleMapper; private final RuleVersionMapper versionMapper; private final ObjectMapper objectMapper;
    public RuleService(ReviewRuleMapper ruleMapper,RuleVersionMapper versionMapper,ObjectMapper objectMapper){this.ruleMapper=ruleMapper;this.versionMapper=versionMapper;this.objectMapper=objectMapper;}

    @Transactional
    public RuleDtos.Response create(RuleDtos.CreateRequest request){
        validateDates(request.effectiveDate(),request.expiryDate()); validateRisk(request.riskLevel());
        if(ruleMapper.selectCount(new LambdaQueryWrapper<ReviewRuleEntity>().eq(ReviewRuleEntity::getRuleCode,request.ruleCode().trim()))>0)
            throw BusinessException.conflict("规则代码已存在");
        compilePattern(request.detectionMethod(),request.ruleExpression());
        ReviewRuleEntity e=new ReviewRuleEntity();e.setTenantId(RequestContext.tenantId());e.setRuleCode(request.ruleCode().trim());
        apply(e,request.ruleName(),request.category(),request.applicableDocumentTypes(),request.riskLevel(),request.detectionMethod(),request.ruleExpression(),request.basis(),request.effectiveDate(),request.expiryDate());
        e.setLifecycleStatus("草稿");e.setCurrentVersion(1);e.setEnabled(true);ruleMapper.insert(e);
        insertVersion(e,request.changeDescription()==null?"创建规则":request.changeDescription());return response(e);
    }

    public List<RuleDtos.Response> list(String category,String lifecycleStatus){return ruleMapper.selectList(new LambdaQueryWrapper<ReviewRuleEntity>()
        .eq(StringUtils.hasText(category),ReviewRuleEntity::getCategory,category).eq(StringUtils.hasText(lifecycleStatus),ReviewRuleEntity::getLifecycleStatus,lifecycleStatus)
        .orderByDesc(ReviewRuleEntity::getUpdatedAt)).stream().map(this::response).toList();}
    public RuleDtos.Response get(String id){return response(require(id));}

    @Transactional
    public RuleDtos.Response update(String id,RuleDtos.UpdateRequest request){ReviewRuleEntity e=require(id);
        if(!List.of("草稿","测试").contains(e.getLifecycleStatus()))throw BusinessException.conflict("只有草稿或测试状态的规则可以编辑");
        validateDates(request.effectiveDate(),request.expiryDate());validateRisk(request.riskLevel());compilePattern(request.detectionMethod(),request.ruleExpression());
        e.setLockVersion(request.lockVersion());apply(e,request.ruleName(),request.category(),request.applicableDocumentTypes(),request.riskLevel(),request.detectionMethod(),request.ruleExpression(),request.basis(),request.effectiveDate(),request.expiryDate());
        e.setCurrentVersion(e.getCurrentVersion()+1);e.setLifecycleStatus("草稿");if(ruleMapper.updateById(e)!=1)throw BusinessException.conflict("规则已被其他用户修改");
        insertVersion(e,request.changeDescription());return get(id);}

    @Transactional
    public RuleDtos.TestResponse test(String id,RuleDtos.TestRequest request){ReviewRuleEntity e=require(id);
        if(!List.of("草稿","测试").contains(e.getLifecycleStatus()))throw BusinessException.conflict("当前状态不允许测试");
        Pattern pattern=compilePattern(e.getDetectionMethod(),e.getRuleExpression());List<RuleDtos.CaseResult> results=new ArrayList<>();int index=0,passed=0;
        for(RuleDtos.TestCase item:request.cases()){Matcher matcher=pattern.matcher(item.text());List<String> matches=new ArrayList<>();while(matcher.find()){matches.add(matcher.group());if(matches.size()>1000)break;}
            boolean ok=matches.size()==item.expectedMatchCount();if(ok)passed++;results.add(new RuleDtos.CaseResult(index++,matches.size(),item.expectedMatchCount(),ok,matches));}
        RuleDtos.TestResponse response=new RuleDtos.TestResponse(passed==request.cases().size(),request.cases().size(),passed,results);
        RuleVersionEntity version=requireVersion(e.getId(),e.getCurrentVersion());try{version.setTestResults(objectMapper.writeValueAsString(response));}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}versionMapper.updateById(version);
        if(response.passed()){e.setLifecycleStatus("测试");ruleMapper.updateById(e);}return response;}

    @Transactional public RuleDtos.Response submit(String id){ReviewRuleEntity e=require(id);if(!"测试".equals(e.getLifecycleStatus()))throw BusinessException.conflict("规则必须通过测试后才能提交审核");e.setLifecycleStatus("审核");ruleMapper.updateById(e);return get(id);}
    @Transactional public RuleDtos.Response publish(String id){ReviewRuleEntity e=require(id);if(!"审核".equals(e.getLifecycleStatus()))throw BusinessException.conflict("只有审核状态的规则可以发布");
        e.setLifecycleStatus("生效");e.setEnabled(true);if(e.getEffectiveDate()==null)e.setEffectiveDate(java.time.LocalDate.now());ruleMapper.updateById(e);
        RuleVersionEntity version=requireVersion(e.getId(),e.getCurrentVersion());version.setApprovedBy(RequestContext.userId());version.setApprovedAt(OffsetDateTime.now());versionMapper.updateById(version);return get(id);}
    @Transactional public RuleDtos.Response deactivate(String id){ReviewRuleEntity e=require(id);if(!"生效".equals(e.getLifecycleStatus()))throw BusinessException.conflict("只有生效规则可以停用");e.setLifecycleStatus("停用");e.setEnabled(false);ruleMapper.updateById(e);return get(id);}
    public List<RuleDtos.VersionResponse> versions(String id){require(id);return versionMapper.selectList(new LambdaQueryWrapper<RuleVersionEntity>().eq(RuleVersionEntity::getRuleId,id).orderByDesc(RuleVersionEntity::getVersionNumber)).stream().map(RuleDtos.VersionResponse::from).toList();}

    private void apply(ReviewRuleEntity e,String name,String category,List<String> types,String risk,String method,String expression,String basis,java.time.LocalDate effective,java.time.LocalDate expiry){
        e.setRuleName(name.trim());e.setCategory(category);e.setApplicableDocumentTypes(write(types==null?List.of():types));e.setRiskLevel(risk);e.setDetectionMethod(method);e.setRuleExpression(expression);e.setBasis(basis);e.setEffectiveDate(effective);e.setExpiryDate(expiry);}
    private void insertVersion(ReviewRuleEntity e,String description){RuleVersionEntity v=new RuleVersionEntity();v.setTenantId(RequestContext.tenantId());v.setRuleId(e.getId());v.setVersionNumber(e.getCurrentVersion());v.setRuleExpression(e.getRuleExpression());v.setChangeDescription(description);versionMapper.insert(v);}
    private ReviewRuleEntity require(String id){ReviewRuleEntity e=ruleMapper.selectById(id);if(e==null)throw BusinessException.notFound("规则不存在");return e;}
    private RuleVersionEntity requireVersion(String id,int number){RuleVersionEntity v=versionMapper.selectOne(new LambdaQueryWrapper<RuleVersionEntity>().eq(RuleVersionEntity::getRuleId,id).eq(RuleVersionEntity::getVersionNumber,number));if(v==null)throw BusinessException.notFound("规则版本不存在");return v;}
    private RuleDtos.Response response(ReviewRuleEntity e){return RuleDtos.Response.from(e,read(e.getApplicableDocumentTypes()));}
    private void validateDates(java.time.LocalDate a,java.time.LocalDate b){if(a!=null&&b!=null&&b.isBefore(a))throw BusinessException.unprocessable("失效日期不得早于生效日期");}
    private void validateRisk(String risk){if(!RISKS.contains(risk))throw BusinessException.unprocessable("无效风险等级");}
    private Pattern compilePattern(String method,String expression){try{String detection=expression.contains("=>")?expression.substring(0,expression.indexOf("=>")):expression;if("正则".equals(method))return Pattern.compile(detection);return Pattern.compile(Pattern.quote(detection));}catch(PatternSyntaxException e){throw BusinessException.unprocessable("规则表达式不是有效正则: "+e.getDescription());}}
    private String write(List<String> value){try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<String> read(String value){try{return value==null?List.of():objectMapper.readValue(value,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException("规则文种数据损坏",e);}}
}
