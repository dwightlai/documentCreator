package cn.gov.document.platform.rule.domain;
import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
@TableName("ai_review_dictionary")
public class ReviewDictionaryEntity extends BaseEntity {
    private String dictCode; private String dictName; private String dictType; private String sourceDescription;
    private LocalDate effectiveDate; private LocalDate expiryDate; private Integer currentVersion; private Integer entryCount;
    public String getDictCode(){return dictCode;} public void setDictCode(String v){dictCode=v;}
    public String getDictName(){return dictName;} public void setDictName(String v){dictName=v;}
    public String getDictType(){return dictType;} public void setDictType(String v){dictType=v;}
    public String getSourceDescription(){return sourceDescription;} public void setSourceDescription(String v){sourceDescription=v;}
    public LocalDate getEffectiveDate(){return effectiveDate;} public void setEffectiveDate(LocalDate v){effectiveDate=v;}
    public LocalDate getExpiryDate(){return expiryDate;} public void setExpiryDate(LocalDate v){expiryDate=v;}
    public Integer getCurrentVersion(){return currentVersion;} public void setCurrentVersion(Integer v){currentVersion=v;}
    public Integer getEntryCount(){return entryCount;} public void setEntryCount(Integer v){entryCount=v;}
}
