package cn.gov.document.platform.rule.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.rule.api.DictionaryDtos;
import cn.gov.document.platform.rule.domain.ReviewDictionaryEntity;
import cn.gov.document.platform.rule.domain.ReviewTermEntity;
import cn.gov.document.platform.rule.persistence.ReviewDictionaryMapper;
import cn.gov.document.platform.rule.persistence.ReviewTermMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DictionaryService {
    private final ReviewDictionaryMapper dictionaryMapper; private final ReviewTermMapper termMapper; private final ObjectMapper objectMapper;
    public DictionaryService(ReviewDictionaryMapper dictionaryMapper,ReviewTermMapper termMapper,ObjectMapper objectMapper){this.dictionaryMapper=dictionaryMapper;this.termMapper=termMapper;this.objectMapper=objectMapper;}

    @Transactional
    public DictionaryDtos.DictionaryResponse createDictionary(DictionaryDtos.CreateDictionaryRequest request){validateDates(request.effectiveDate(),request.expiryDate());
        if(dictionaryMapper.selectCount(new LambdaQueryWrapper<ReviewDictionaryEntity>().eq(ReviewDictionaryEntity::getDictCode,request.dictCode().trim()))>0)throw BusinessException.conflict("词库代码已存在");
        ReviewDictionaryEntity e=new ReviewDictionaryEntity();e.setTenantId(RequestContext.tenantId());e.setDictCode(request.dictCode().trim());e.setDictName(request.dictName().trim());e.setDictType(request.dictType());e.setSourceDescription(request.sourceDescription());e.setEffectiveDate(request.effectiveDate());e.setExpiryDate(request.expiryDate());e.setCurrentVersion(1);e.setEntryCount(0);dictionaryMapper.insert(e);return DictionaryDtos.DictionaryResponse.from(e);}
    public List<DictionaryDtos.DictionaryResponse> dictionaries(String type){return dictionaryMapper.selectList(new LambdaQueryWrapper<ReviewDictionaryEntity>().eq(StringUtils.hasText(type),ReviewDictionaryEntity::getDictType,type).orderByAsc(ReviewDictionaryEntity::getDictName)).stream().map(DictionaryDtos.DictionaryResponse::from).toList();}
    public DictionaryDtos.DictionaryResponse dictionary(String id){return DictionaryDtos.DictionaryResponse.from(requireDictionary(id));}
    public List<DictionaryDtos.TermResponse> terms(String dictionaryId,String keyword,String category){requireDictionary(dictionaryId);return termMapper.selectList(new LambdaQueryWrapper<ReviewTermEntity>()
        .eq(ReviewTermEntity::getDictionaryId,dictionaryId).like(StringUtils.hasText(keyword),ReviewTermEntity::getTermText,keyword)
        .eq(StringUtils.hasText(category),ReviewTermEntity::getCategoryTag,category).orderByAsc(ReviewTermEntity::getTermText)).stream().map(this::response).toList();}

    @Transactional public DictionaryDtos.TermResponse createTerm(String dictionaryId,DictionaryDtos.CreateTermRequest request){ReviewDictionaryEntity dictionary=requireDictionary(dictionaryId);validateDates(request.effectiveDate(),request.expiryDate());
        ensureUnique(dictionaryId,request.termText(),null);ReviewTermEntity e=new ReviewTermEntity();e.setTenantId(RequestContext.tenantId());e.setDictionaryId(dictionaryId);apply(e,request.termText(),request.standardForm(),request.aliases(),request.categoryTag(),request.riskLevel(),request.sourceReference(),request.effectiveDate(),request.expiryDate(),request.usageNote());termMapper.insert(e);refreshCount(dictionary);return response(e);}
    @Transactional public DictionaryDtos.TermResponse updateTerm(String dictionaryId,String termId,DictionaryDtos.UpdateTermRequest request){ReviewDictionaryEntity dictionary=requireDictionary(dictionaryId);ReviewTermEntity e=requireTerm(dictionaryId,termId);validateDates(request.effectiveDate(),request.expiryDate());ensureUnique(dictionaryId,request.termText(),termId);e.setLockVersion(request.lockVersion());apply(e,request.termText(),request.standardForm(),request.aliases(),request.categoryTag(),request.riskLevel(),request.sourceReference(),request.effectiveDate(),request.expiryDate(),request.usageNote());if(termMapper.updateById(e)!=1)throw BusinessException.conflict("词条已被其他用户修改");dictionary.setCurrentVersion(dictionary.getCurrentVersion()+1);dictionaryMapper.updateById(dictionary);return response(termMapper.selectById(termId));}
    @Transactional public void deleteTerm(String dictionaryId,String termId){ReviewDictionaryEntity dictionary=requireDictionary(dictionaryId);requireTerm(dictionaryId,termId);termMapper.deleteById(termId);refreshCount(dictionary);}

    @Transactional
    public DictionaryDtos.ImportResponse importCsv(String dictionaryId,MultipartFile file){ReviewDictionaryEntity dictionary=requireDictionary(dictionaryId);if(file==null||file.isEmpty())throw BusinessException.unprocessable("请选择 CSV 文件");if(file.getSize()>5*1024*1024)throw BusinessException.unprocessable("CSV 文件不能超过 5MB");
        String content;try{content=new String(file.getBytes(),StandardCharsets.UTF_8);}catch(IOException e){throw BusinessException.unprocessable("CSV 文件读取失败");}
        content=content.replaceFirst("^\\uFEFF","");String[] lines=content.split("\\R");if(lines.length<2)throw BusinessException.unprocessable("CSV 至少需要表头和一行数据");if(lines.length>10001)throw BusinessException.unprocessable("单次导入不能超过 10000 条");
        List<String> headers=parseCsvLine(lines[0]);Map<String,Integer> columns=new HashMap<>();for(int i=0;i<headers.size();i++)columns.put(headers.get(i).trim(),i);if(!columns.containsKey("term_text"))throw BusinessException.unprocessable("CSV 缺少 term_text 表头");
        int imported=0,skipped=0;List<String> errors=new ArrayList<>();Set<String> batchTerms=new HashSet<>();
        for(int row=1;row<lines.length;row++){if(lines[row].isBlank())continue;List<String> values=parseCsvLine(lines[row]);String term=value(values,columns,"term_text");
            try{if(!StringUtils.hasText(term))throw BusinessException.unprocessable("term_text 不能为空");if(!batchTerms.add(term.trim()))throw BusinessException.conflict("文件内词条重复");
                createTerm(dictionaryId,new DictionaryDtos.CreateTermRequest(term,value(values,columns,"standard_form"),splitAliases(value(values,columns,"aliases")),value(values,columns,"category_tag"),value(values,columns,"risk_level"),value(values,columns,"source_reference"),parseDate(value(values,columns,"effective_date")),parseDate(value(values,columns,"expiry_date")),value(values,columns,"usage_note")));imported++;
            }catch(RuntimeException e){skipped++;if(errors.size()<100)errors.add("第 "+(row+1)+" 行："+e.getMessage());}}
        refreshCount(dictionary);return new DictionaryDtos.ImportResponse(lines.length-1,imported,skipped,errors);}

    private void apply(ReviewTermEntity e,String text,String standard,List<String> aliases,String category,String risk,String source,LocalDate effective,LocalDate expiry,String note){e.setTermText(text.trim());e.setStandardForm(standard);e.setAliases(write(aliases==null?List.of():aliases));e.setCategoryTag(category);e.setRiskLevel(risk);e.setSourceReference(source);e.setEffectiveDate(effective);e.setExpiryDate(expiry);e.setUsageNote(note);}
    private void ensureUnique(String dictionaryId,String text,String excludedId){ReviewTermEntity found=termMapper.selectOne(new LambdaQueryWrapper<ReviewTermEntity>().eq(ReviewTermEntity::getDictionaryId,dictionaryId).eq(ReviewTermEntity::getTermText,text.trim()));if(found!=null&&!found.getId().equals(excludedId))throw BusinessException.conflict("词条已存在");}
    private ReviewDictionaryEntity requireDictionary(String id){ReviewDictionaryEntity e=dictionaryMapper.selectById(id);if(e==null)throw BusinessException.notFound("词库不存在");return e;}
    private ReviewTermEntity requireTerm(String dictionaryId,String id){ReviewTermEntity e=termMapper.selectById(id);if(e==null||!dictionaryId.equals(e.getDictionaryId()))throw BusinessException.notFound("词条不存在");return e;}
    private void refreshCount(ReviewDictionaryEntity dictionary){dictionary.setEntryCount(Math.toIntExact(termMapper.selectCount(new LambdaQueryWrapper<ReviewTermEntity>().eq(ReviewTermEntity::getDictionaryId,dictionary.getId()))));dictionary.setCurrentVersion(dictionary.getCurrentVersion()+1);dictionaryMapper.updateById(dictionary);}
    private DictionaryDtos.TermResponse response(ReviewTermEntity e){return DictionaryDtos.TermResponse.from(e,read(e.getAliases()));}
    private void validateDates(LocalDate a,LocalDate b){if(a!=null&&b!=null&&b.isBefore(a))throw BusinessException.unprocessable("失效日期不得早于生效日期");}
    private String write(List<String> values){try{return objectMapper.writeValueAsString(values);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<String> read(String value){try{return value==null?List.of():objectMapper.readValue(value,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException("词条别名数据损坏",e);}}
    private List<String> splitAliases(String value){return StringUtils.hasText(value)?java.util.Arrays.stream(value.split("[|；;]" )).map(String::trim).filter(StringUtils::hasText).toList():List.of();}
    private LocalDate parseDate(String value){if(!StringUtils.hasText(value))return null;try{return LocalDate.parse(value.trim());}catch(java.time.format.DateTimeParseException e){throw BusinessException.unprocessable("日期须为 YYYY-MM-DD");}}
    private String value(List<String> values,Map<String,Integer> columns,String key){Integer index=columns.get(key);return index==null||index>=values.size()?null:values.get(index).trim();}
    private List<String> parseCsvLine(String line){List<String> result=new ArrayList<>();StringBuilder current=new StringBuilder();boolean quoted=false;for(int i=0;i<line.length();i++){char ch=line.charAt(i);if(ch=='\"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='\"'){current.append('\"');i++;}else quoted=!quoted;}else if(ch==','&&!quoted){result.add(current.toString());current.setLength(0);}else current.append(ch);}if(quoted)throw BusinessException.unprocessable("CSV 引号未闭合");result.add(current.toString());return result;}
}
