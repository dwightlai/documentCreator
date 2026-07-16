package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.review.api.ReviewPlanDtos;
import cn.gov.document.platform.review.domain.ReviewPlanEntity;
import cn.gov.document.platform.review.persistence.ReviewPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service public class ReviewPlanService{
    private final ReviewPlanMapper mapper;private final ObjectMapper objectMapper;public ReviewPlanService(ReviewPlanMapper mapper,ObjectMapper objectMapper){this.mapper=mapper;this.objectMapper=objectMapper;}
    @Transactional public List<ReviewPlanDtos.Response> list(){ensureBuiltIns();return mapper.selectList(new LambdaQueryWrapper<ReviewPlanEntity>().orderByAsc(ReviewPlanEntity::getIsCustom).orderByAsc(ReviewPlanEntity::getPlanCode)).stream().map(this::response).toList();}
    public ReviewPlanDtos.Response get(String id){return response(require(id));}
    @Transactional public ReviewPlanDtos.Response create(ReviewPlanDtos.Request request){ensureUnique(request.planCode(),null);ReviewPlanEntity entity=new ReviewPlanEntity();entity.setTenantId(RequestContext.tenantId());entity.setIsCustom(true);apply(entity,request);mapper.insert(entity);return response(entity);}
    @Transactional public ReviewPlanDtos.Response update(String id,ReviewPlanDtos.Request request){ReviewPlanEntity entity=require(id);if(!Boolean.TRUE.equals(entity.getIsCustom()))throw BusinessException.conflict("内置审校方案不可修改");if(request.lockVersion()==null)throw BusinessException.unprocessable("更新审校方案必须提交 lockVersion");ensureUnique(request.planCode(),id);entity.setLockVersion(request.lockVersion());apply(entity,request);if(mapper.updateById(entity)!=1)throw BusinessException.conflict("审校方案已被其他用户修改");return get(id);}
    @Transactional public String defaultPlanId(){ensureBuiltIns();ReviewPlanEntity entity=mapper.selectOne(new LambdaQueryWrapper<ReviewPlanEntity>().eq(ReviewPlanEntity::getPlanCode,"OFFICIAL"));return entity.getId();}
    public void verify(String id){require(id);}
    public RuntimePlan runtime(String id){ReviewPlanEntity entity=require(id);return new RuntimePlan(read(entity.getReviewLevels()),entity.getRiskThreshold());}
    private void ensureBuiltIns(){if(mapper.selectCount(new LambdaQueryWrapper<ReviewPlanEntity>().in(ReviewPlanEntity::getPlanCode,List.of("QUICK","OFFICIAL","DEEP","SECURITY")))==4)return;insertBuiltIn("QUICK","快速校对","日常材料",List.of("L1","L2"),"一般",false);insertBuiltIn("OFFICIAL","公文规范审校","正式公文拟稿",List.of("L1","L2","L3","L4"),"一般",false);insertBuiltIn("DEEP","深度内容审校","领导讲话、报告",List.of("L1","L2","L3","L4","L5","L6"),"提示",false);insertBuiltIn("SECURITY","内容安全审校","对外发布材料",List.of("L1","L2","L3","L4","L5","L6","L7"),"提示",false);}
    private void insertBuiltIn(String code,String name,String scene,List<String> levels,String risk,boolean external){if(mapper.selectCount(new LambdaQueryWrapper<ReviewPlanEntity>().eq(ReviewPlanEntity::getPlanCode,code))>0)return;ReviewPlanEntity e=new ReviewPlanEntity();e.setTenantId(RequestContext.tenantId());e.setPlanCode(code);e.setPlanName(name);e.setApplicableScenarios(scene);e.setReviewLevels(write(levels));e.setKnowledgeScope("[]");e.setRuleSetVersion("builtin-v1");e.setAllowExternalModel(external);e.setRiskThreshold(risk);e.setCheckAttachments(true);e.setAllowAutoFix(false);e.setGenerateReport(true);e.setIsCustom(false);mapper.insert(e);}
    private void apply(ReviewPlanEntity e,ReviewPlanDtos.Request r){e.setPlanCode(r.planCode().trim());e.setPlanName(r.planName().trim());e.setApplicableScenarios(r.applicableScenarios());e.setReviewLevels(write(r.reviewLevels()));e.setRuleSetVersion(r.ruleSetVersion());e.setKnowledgeScope(write(r.knowledgeScope()==null?List.of():r.knowledgeScope()));e.setAllowExternalModel(r.allowExternalModel());e.setRiskThreshold(r.riskThreshold());e.setCheckAttachments(r.checkAttachments());e.setAllowAutoFix(r.allowAutoFix());e.setGenerateReport(r.generateReport());}
    private void ensureUnique(String code,String excluded){ReviewPlanEntity found=mapper.selectOne(new LambdaQueryWrapper<ReviewPlanEntity>().eq(ReviewPlanEntity::getPlanCode,code.trim()));if(found!=null&&!found.getId().equals(excluded))throw BusinessException.conflict("审校方案代码已存在");}
    private ReviewPlanEntity require(String id){ReviewPlanEntity e=mapper.selectById(id);if(e==null)throw BusinessException.notFound("审校方案不存在");return e;}
    private ReviewPlanDtos.Response response(ReviewPlanEntity e){return ReviewPlanDtos.Response.from(e,read(e.getReviewLevels()),read(e.getKnowledgeScope()));}
    private String write(List<String> values){try{return objectMapper.writeValueAsString(values);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<String> read(String value){try{return value==null?List.of():objectMapper.readValue(value,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException("审校方案数组数据损坏",e);}}
    public record RuntimePlan(List<String> levels,String riskThreshold){}
}
