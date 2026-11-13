package cn.gov.document.platform.rule.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.rule.api.DictionaryDtos;
import cn.gov.document.platform.rule.api.RuleDataExchangeDtos;
import cn.gov.document.platform.rule.api.RuleDtos;
import cn.gov.document.platform.rule.domain.ReviewDictionaryEntity;
import cn.gov.document.platform.rule.domain.ReviewTermEntity;
import cn.gov.document.platform.rule.persistence.ReviewDictionaryMapper;
import cn.gov.document.platform.rule.persistence.ReviewTermMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleDataExchangeService {
    private static final String RULE_FORMAT = "gov-document-rules";
    private static final String DICTIONARY_FORMAT = "gov-document-dictionaries";
    private final RuleService rules;
    private final DictionaryService dictionaries;
    private final ReviewDictionaryMapper dictionaryMapper;
    private final ReviewTermMapper termMapper;
    private final ObjectMapper mapper;
    private final Validator validator;

    public RuleDataExchangeService(
            RuleService rules,
            DictionaryService dictionaries,
            ReviewDictionaryMapper dictionaryMapper,
            ReviewTermMapper termMapper,
            ObjectMapper mapper,
            Validator validator) {
        this.rules = rules;
        this.dictionaries = dictionaries;
        this.dictionaryMapper = dictionaryMapper;
        this.termMapper = termMapper;
        this.mapper = mapper;
        this.validator = validator;
    }

    public RuleDataExchangeDtos.RuleBundle exportRules() {
        List<RuleDataExchangeDtos.RuleItem> items = rules.list(null, null).stream()
                .map(r -> new RuleDataExchangeDtos.RuleItem(
                        r.ruleCode(),
                        r.ruleName(),
                        r.category(),
                        r.applicableDocumentTypes(),
                        r.riskLevel(),
                        r.detectionMethod(),
                        r.ruleExpression(),
                        r.basis(),
                        r.effectiveDate(),
                        r.expiryDate(),
                        r.lifecycleStatus()))
                .toList();
        return new RuleDataExchangeDtos.RuleBundle(RULE_FORMAT, 1, OffsetDateTime.now(), items);
    }

    @Transactional
    public RuleDataExchangeDtos.ImportResult importRules(RuleDataExchangeDtos.RuleBundle bundle) {
        if (bundle == null
                || !RULE_FORMAT.equals(bundle.format())
                || bundle.version() != 1
                || bundle.rules() == null
                || bundle.rules().isEmpty()
                || bundle.rules().size() > 1000) throw BusinessException.unprocessable("规则文件格式无效，单次最多导入 1000 条");
        Set<String> fileCodes = new HashSet<>();
        for (var item : bundle.rules()) {
            if (item == null) throw BusinessException.unprocessable("规则文件包含空记录");
            var request = ruleRequest(item);
            validate(request, "规则 " + item.ruleCode());
            if (!List.of("词项", "正则", "人工核验").contains(item.detectionMethod()))
                throw BusinessException.unprocessable("规则 " + item.ruleCode() + " 的检测方式无效");
            if (!fileCodes.add(item.ruleCode().trim()))
                throw BusinessException.unprocessable("规则文件内代码重复：" + item.ruleCode());
        }
        Set<String> existing = new HashSet<>();
        for (var rule : rules.list(null, null)) existing.add(rule.ruleCode());
        int imported = 0;
        List<String> skipped = new ArrayList<>();
        for (var item : bundle.rules()) {
            if (existing.contains(item.ruleCode().trim())) {
                skipped.add(item.ruleCode());
                continue;
            }
            try {
                rules.create(ruleRequest(item));
            } catch (BusinessException e) {
                throw BusinessException.unprocessable("规则 " + item.ruleCode() + " 导入失败：" + e.getMessage());
            }
            imported++;
        }
        return new RuleDataExchangeDtos.ImportResult(bundle.rules().size(), imported, skipped.size(), 0, skipped);
    }

    public RuleDataExchangeDtos.DictionaryBundle exportDictionaries() {
        List<RuleDataExchangeDtos.DictionaryItem> items = dictionaries.dictionaries(null).stream()
                .map(d -> new RuleDataExchangeDtos.DictionaryItem(
                        d.dictCode(),
                        d.dictName(),
                        d.dictType(),
                        d.sourceDescription(),
                        d.effectiveDate(),
                        d.expiryDate(),
                        dictionaries.terms(d.id(), null, null).stream()
                                .map(t -> new RuleDataExchangeDtos.TermItem(
                                        t.termText(),
                                        t.standardForm(),
                                        t.aliases(),
                                        t.categoryTag(),
                                        t.riskLevel(),
                                        t.sourceReference(),
                                        t.effectiveDate(),
                                        t.expiryDate(),
                                        t.usageNote()))
                                .toList()))
                .toList();
        return new RuleDataExchangeDtos.DictionaryBundle(DICTIONARY_FORMAT, 1, OffsetDateTime.now(), items);
    }

    @Transactional
    public RuleDataExchangeDtos.ImportResult importDictionaries(RuleDataExchangeDtos.DictionaryBundle bundle) {
        if (bundle == null
                || !DICTIONARY_FORMAT.equals(bundle.format())
                || bundle.version() != 1
                || bundle.dictionaries() == null
                || bundle.dictionaries().isEmpty()
                || bundle.dictionaries().size() > 100) throw BusinessException.unprocessable("词库文件格式无效，单次最多导入 100 部");
        Set<String> fileCodes = new HashSet<>();
        int totalTerms = 0;
        for (var item : bundle.dictionaries()) {
            if (item == null) throw BusinessException.unprocessable("词库文件包含空记录");
            validate(
                    new DictionaryDtos.CreateDictionaryRequest(
                            item.dictCode(),
                            item.dictName(),
                            item.dictType(),
                            item.sourceDescription(),
                            item.effectiveDate(),
                            item.expiryDate()),
                    "词库 " + item.dictCode());
            if (item.effectiveDate() != null
                    && item.expiryDate() != null
                    && item.expiryDate().isBefore(item.effectiveDate()))
                throw BusinessException.unprocessable("词库 " + item.dictCode() + " 的失效日期早于生效日期");
            if (!fileCodes.add(item.dictCode().trim()))
                throw BusinessException.unprocessable("词库文件内代码重复：" + item.dictCode());
            if (item.terms() == null) throw BusinessException.unprocessable("词库 " + item.dictCode() + " 缺少词条列表");
            totalTerms += item.terms().size();
            if (totalTerms > 10000) throw BusinessException.unprocessable("单次最多导入 10000 条词条");
            Set<String> termTexts = new HashSet<>();
            for (var term : item.terms()) {
                if (term == null) throw BusinessException.unprocessable("词库 " + item.dictCode() + " 包含空词条");
                validate(
                        new DictionaryDtos.CreateTermRequest(
                                term.termText(),
                                term.standardForm(),
                                term.aliases(),
                                term.categoryTag(),
                                term.riskLevel(),
                                term.sourceReference(),
                                term.effectiveDate(),
                                term.expiryDate(),
                                term.usageNote()),
                        "词库 " + item.dictCode() + " 的词条");
                if (term.effectiveDate() != null
                        && term.expiryDate() != null
                        && term.expiryDate().isBefore(term.effectiveDate()))
                    throw BusinessException.unprocessable("词条 " + term.termText() + " 的失效日期早于生效日期");
                if (!termTexts.add(term.termText().trim()))
                    throw BusinessException.unprocessable("词库 " + item.dictCode() + " 内词条重复：" + term.termText());
            }
        }
        Set<String> existing = new HashSet<>();
        for (var dictionary : dictionaries.dictionaries(null)) existing.add(dictionary.dictCode());
        int imported = 0, termsImported = 0;
        List<String> skipped = new ArrayList<>();
        for (var item : bundle.dictionaries()) {
            if (existing.contains(item.dictCode().trim())) {
                skipped.add(item.dictCode());
                continue;
            }
            ReviewDictionaryEntity dictionary = new ReviewDictionaryEntity();
            dictionary.setTenantId(RequestContext.tenantId());
            dictionary.setDictCode(item.dictCode().trim());
            dictionary.setDictName(item.dictName().trim());
            dictionary.setDictType(item.dictType().trim());
            dictionary.setSourceDescription(item.sourceDescription());
            dictionary.setEffectiveDate(item.effectiveDate());
            dictionary.setExpiryDate(item.expiryDate());
            dictionary.setCurrentVersion(1);
            dictionary.setEntryCount(item.terms().size());
            dictionaryMapper.insert(dictionary);
            for (var itemTerm : item.terms()) {
                ReviewTermEntity term = new ReviewTermEntity();
                term.setTenantId(RequestContext.tenantId());
                term.setDictionaryId(dictionary.getId());
                term.setTermText(itemTerm.termText().trim());
                term.setStandardForm(itemTerm.standardForm());
                term.setAliases(write(itemTerm.aliases() == null ? List.of() : itemTerm.aliases()));
                term.setCategoryTag(itemTerm.categoryTag());
                term.setRiskLevel(itemTerm.riskLevel());
                term.setSourceReference(itemTerm.sourceReference());
                term.setEffectiveDate(itemTerm.effectiveDate());
                term.setExpiryDate(itemTerm.expiryDate());
                term.setUsageNote(itemTerm.usageNote());
                termMapper.insert(term);
                termsImported++;
            }
            imported++;
        }
        return new RuleDataExchangeDtos.ImportResult(
                bundle.dictionaries().size(), imported, skipped.size(), termsImported, skipped);
    }

    private RuleDtos.CreateRequest ruleRequest(RuleDataExchangeDtos.RuleItem item) {
        return new RuleDtos.CreateRequest(
                item.ruleCode(),
                item.ruleName(),
                item.category(),
                item.applicableDocumentTypes(),
                item.riskLevel(),
                item.detectionMethod(),
                item.ruleExpression(),
                item.basis(),
                item.effectiveDate(),
                item.expiryDate(),
                "从文件导入");
    }

    private <T> void validate(T bean, String label) {
        Set<ConstraintViolation<T>> violations = validator.validate(bean);
        if (!violations.isEmpty())
            throw BusinessException.unprocessable(
                    label + "：" + violations.iterator().next().getMessage());
    }

    private String write(List<String> values) {
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw BusinessException.unprocessable("词条别名无法写入");
        }
    }
}
