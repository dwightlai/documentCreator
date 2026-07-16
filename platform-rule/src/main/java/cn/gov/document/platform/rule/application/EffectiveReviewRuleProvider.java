package cn.gov.document.platform.rule.application;

import cn.gov.document.platform.rule.domain.ReviewDictionaryEntity;
import cn.gov.document.platform.rule.domain.ReviewRuleEntity;
import cn.gov.document.platform.rule.domain.ReviewTermEntity;
import cn.gov.document.platform.rule.persistence.ReviewDictionaryMapper;
import cn.gov.document.platform.rule.persistence.ReviewRuleMapper;
import cn.gov.document.platform.rule.persistence.ReviewTermMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Loads tenant-scoped, currently effective rules and dictionary terms for the review engine.
 * MyBatis-Plus tenant interception supplies the tenant predicate for every query.
 */
@Service
public class EffectiveReviewRuleProvider {
    private final ReviewRuleMapper ruleMapper;
    private final ReviewDictionaryMapper dictionaryMapper;
    private final ReviewTermMapper termMapper;
    private final ObjectMapper objectMapper;

    public EffectiveReviewRuleProvider(ReviewRuleMapper ruleMapper, ReviewDictionaryMapper dictionaryMapper,
                                       ReviewTermMapper termMapper, ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.dictionaryMapper = dictionaryMapper;
        this.termMapper = termMapper;
        this.objectMapper = objectMapper;
    }

    public List<Match> inspect(String documentTypeCode, String text) {
        String source = text == null ? "" : text;
        LocalDate today = LocalDate.now();
        List<Match> result = new ArrayList<>();
        Set<String> deduplication = new HashSet<>();

        List<ReviewRuleEntity> rules = ruleMapper.selectList(new LambdaQueryWrapper<ReviewRuleEntity>()
            .eq(ReviewRuleEntity::getLifecycleStatus, "生效")
            .eq(ReviewRuleEntity::getEnabled, true));
        for (ReviewRuleEntity rule : rules) {
            if (!isEffective(rule.getEffectiveDate(), rule.getExpiryDate(), today)
                || !supportsDocumentType(rule.getApplicableDocumentTypes(), documentTypeCode)) {
                continue;
            }
            addRuleMatches(result, deduplication, rule, source);
        }

        List<String> activeDictionaryIds = dictionaryMapper.selectList(new LambdaQueryWrapper<ReviewDictionaryEntity>())
            .stream()
            .filter(dictionary -> isEffective(dictionary.getEffectiveDate(), dictionary.getExpiryDate(), today))
            .map(ReviewDictionaryEntity::getId)
            .toList();
        if (!activeDictionaryIds.isEmpty()) {
            for (ReviewTermEntity term : termMapper.selectList(new LambdaQueryWrapper<ReviewTermEntity>()
                .in(ReviewTermEntity::getDictionaryId, activeDictionaryIds))) {
                if (isEffective(term.getEffectiveDate(), term.getExpiryDate(), today)) {
                    addTermMatches(result, deduplication, term, source);
                }
            }
        }
        return result;
    }

    private void addRuleMatches(List<Match> result, Set<String> deduplication, ReviewRuleEntity rule, String text) {
        Expression expression = parseExpression(rule.getDetectionMethod(), rule.getRuleExpression());
        Matcher matcher = expression.pattern().matcher(text);
        int guard = 0;
        while (matcher.find() && guard++ < 10_000) {
            String original = matcher.group();
            String replacement = expression.replacement() == null ? original
                : expression.pattern().matcher(original).replaceFirst(expression.replacement());
            add(result, deduplication, new Match(rule.getRuleCode(), rule.getCategory(), defaultRisk(rule.getRiskLevel()),
                matcher.start(), matcher.end(), original, replacement,
                StringUtils.hasText(rule.getRuleName()) ? rule.getRuleName() : "命中已发布审校规则",
                rule.getBasis(), "规则", new BigDecimal("0.96")));
        }
    }

    private void addTermMatches(List<Match> result, Set<String> deduplication, ReviewTermEntity term, String text) {
        List<String> variants = new ArrayList<>();
        variants.add(term.getTermText());
        variants.addAll(readAliases(term.getAliases()));
        for (String variant : variants.stream().filter(StringUtils::hasText).distinct().toList()) {
            int from = 0;
            while ((from = text.indexOf(variant, from)) >= 0) {
                String replacement = StringUtils.hasText(term.getStandardForm()) ? term.getStandardForm() : term.getTermText();
                add(result, deduplication, new Match("DICT:" + term.getId(),
                    StringUtils.hasText(term.getCategoryTag()) ? term.getCategoryTag() : "词语规范",
                    defaultRisk(term.getRiskLevel()), from, from + variant.length(), variant, replacement,
                    StringUtils.hasText(term.getUsageNote()) ? term.getUsageNote() : "命中有效词库词条",
                    term.getSourceReference(), "词库", new BigDecimal("0.95")));
                from += Math.max(variant.length(), 1);
            }
        }
    }

    private void add(List<Match> result, Set<String> deduplication, Match match) {
        String key = match.start() + ":" + match.end() + ":" + match.original() + ":" + match.replacement();
        if (deduplication.add(key)) {
            result.add(match);
        }
    }

    private Expression parseExpression(String method, String value) {
        String expression = value == null ? "" : value;
        int separator = expression.indexOf("=>");
        String detection = separator >= 0 ? expression.substring(0, separator) : expression;
        String replacement = separator >= 0 ? expression.substring(separator + 2) : null;
        try {
            Pattern pattern = "正则".equals(method) ? Pattern.compile(detection) : Pattern.compile(Pattern.quote(detection));
            return new Expression(pattern, replacement);
        } catch (PatternSyntaxException exception) {
            // Publishing validates expressions. Ignore a corrupted legacy row instead of aborting the whole review task.
            return new Expression(Pattern.compile("(?!x)x"), null);
        }
    }

    private boolean supportsDocumentType(String json, String documentTypeCode) {
        if (!StringUtils.hasText(json)) {
            return true;
        }
        try {
            List<String> types = objectMapper.readValue(json, new TypeReference<>() { });
            return types.isEmpty() || !StringUtils.hasText(documentTypeCode) || types.contains(documentTypeCode);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private List<String> readAliases(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private boolean isEffective(LocalDate effectiveDate, LocalDate expiryDate, LocalDate today) {
        return (effectiveDate == null || !effectiveDate.isAfter(today))
            && (expiryDate == null || !expiryDate.isBefore(today));
    }

    private String defaultRisk(String risk) {
        return StringUtils.hasText(risk) ? risk : "提示";
    }

    private record Expression(Pattern pattern, String replacement) { }

    public record Match(String sourceCode, String category, String risk, int start, int end,
                        String original, String replacement, String explanation, String basis,
                        String detectionMethod, BigDecimal confidence) { }
}
