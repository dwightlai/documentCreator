package cn.gov.document.platform.rule.api;

import cn.gov.document.platform.rule.domain.ReviewDictionaryEntity;
import cn.gov.document.platform.rule.domain.ReviewTermEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class DictionaryDtos {
    private DictionaryDtos(){}
    public record CreateDictionaryRequest(@NotBlank @Size(max=50) String dictCode,@NotBlank @Size(max=200) String dictName,
        @NotBlank String dictType,String sourceDescription,LocalDate effectiveDate,LocalDate expiryDate){}
    public record DictionaryResponse(String id,String dictCode,String dictName,String dictType,String sourceDescription,
        LocalDate effectiveDate,LocalDate expiryDate,int currentVersion,int entryCount,int lockVersion,OffsetDateTime updatedAt){
        public static DictionaryResponse from(ReviewDictionaryEntity e){return new DictionaryResponse(e.getId(),e.getDictCode(),e.getDictName(),e.getDictType(),
            e.getSourceDescription(),e.getEffectiveDate(),e.getExpiryDate(),e.getCurrentVersion(),e.getEntryCount(),e.getLockVersion(),e.getUpdatedAt());}
    }
    public record CreateTermRequest(@NotBlank @Size(max=500) String termText,@Size(max=500) String standardForm,List<String> aliases,
        String categoryTag,String riskLevel,String sourceReference,LocalDate effectiveDate,LocalDate expiryDate,String usageNote){}
    public record UpdateTermRequest(@NotBlank @Size(max=500) String termText,@Size(max=500) String standardForm,List<String> aliases,
        String categoryTag,String riskLevel,String sourceReference,LocalDate effectiveDate,LocalDate expiryDate,String usageNote,@NotNull Integer lockVersion){}
    public record TermResponse(String id,String dictionaryId,String termText,String standardForm,List<String> aliases,
        String categoryTag,String riskLevel,String sourceReference,LocalDate effectiveDate,LocalDate expiryDate,
        String usageNote,int lockVersion,OffsetDateTime updatedAt){
        public static TermResponse from(ReviewTermEntity e,List<String> aliases){return new TermResponse(e.getId(),e.getDictionaryId(),e.getTermText(),e.getStandardForm(),aliases,
            e.getCategoryTag(),e.getRiskLevel(),e.getSourceReference(),e.getEffectiveDate(),e.getExpiryDate(),e.getUsageNote(),e.getLockVersion(),e.getUpdatedAt());}
    }
    public record ImportResponse(int totalRows,int importedRows,int skippedRows,List<String> errors){}
}
