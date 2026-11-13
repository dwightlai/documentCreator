package cn.gov.document.platform.rule.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class RuleDataExchangeDtos {
    private RuleDataExchangeDtos() {}

    public record RuleItem(
            String ruleCode,
            String ruleName,
            String category,
            List<String> applicableDocumentTypes,
            String riskLevel,
            String detectionMethod,
            String ruleExpression,
            String basis,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String lifecycleStatus) {}

    public record RuleBundle(String format, int version, OffsetDateTime exportedAt, List<RuleItem> rules) {}

    public record TermItem(
            String termText,
            String standardForm,
            List<String> aliases,
            String categoryTag,
            String riskLevel,
            String sourceReference,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String usageNote) {}

    public record DictionaryItem(
            String dictCode,
            String dictName,
            String dictType,
            String sourceDescription,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            List<TermItem> terms) {}

    public record DictionaryBundle(
            String format, int version, OffsetDateTime exportedAt, List<DictionaryItem> dictionaries) {}

    public record ImportResult(int total, int imported, int skipped, int termsImported, List<String> skippedCodes) {}
}
