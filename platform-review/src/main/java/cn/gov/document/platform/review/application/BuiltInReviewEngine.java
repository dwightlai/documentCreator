package cn.gov.document.platform.review.application;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.rule.application.EffectiveReviewRuleProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BuiltInReviewEngine {
    private final EffectiveReviewRuleProvider effectiveRuleProvider;

    private final cn.gov.document.platform.review.application.deep.FactDataInspector factInspector =
            new cn.gov.document.platform.review.application.deep.FactDataInspector();
    private final cn.gov.document.platform.review.application.deep.LogicInspector logicInspector =
            new cn.gov.document.platform.review.application.deep.LogicInspector();
    private final cn.gov.document.platform.review.application.deep.ContentSafetyInspector safetyInspector =
            new cn.gov.document.platform.review.application.deep.ContentSafetyInspector();

    public BuiltInReviewEngine(EffectiveReviewRuleProvider effectiveRuleProvider) {
        this.effectiveRuleProvider = effectiveRuleProvider;
    }

    private static final List<Rule> RULES = List.of(
            new Rule("L1", "文字", "一般", "截止目前为止", "截至目前", "“截止”表示到某时停止，此处宜使用“截至”。", "现代汉语规范用法", new BigDecimal("0.98")),
            new Rule("L2", "规范", "一般", "做出贡献", "作出贡献", "固定搭配宜使用“作出贡献”。", "党政机关公文常用词语规范", new BigDecimal("0.96")),
            new Rule("L1", "文字", "提示", "帐号", "账号", "信息化语境下统一使用“账号”。", "信息技术术语规范", new BigDecimal("0.95")),
            new Rule("L2", "规范", "重要", "绝对不会", "原则上不会", "绝对化表述可能形成不当承诺，建议审慎表述。", "政务公开表述风险规则", new BigDecimal("0.88")));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-zA-Z][a-zA-Z0-9_]*}");
    private static final Pattern REQUEST_IN_REPORT = Pattern.compile("请(?:批准|批复|同意|审定|审批)");
    private static final Pattern REQUEST_MATTER = Pattern.compile("请(?:批准|批复|同意|审定|审批|予以支持)");
    private static final Pattern REQUEST_ENDING = Pattern.compile("(?:妥否|当否)[，,]?请批示|以上请示[^。；]{0,20}请(?:批复|示)|请予批复");

    public List<Candidate> inspect(
            String documentTypeCode,
            List<DocumentParagraphEntity> paragraphs,
            Set<String> enabledLevels,
            String riskThreshold) {
        return inspect(documentTypeCode, null, paragraphs, enabledLevels, riskThreshold);
    }

    public List<Candidate> inspect(
            String documentTypeCode,
            String documentTitle,
            List<DocumentParagraphEntity> paragraphs,
            Set<String> enabledLevels,
            String riskThreshold) {
        List<Candidate> result = new ArrayList<>();
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            for (Rule rule : RULES) {
                if (!enabledLevels.contains(rule.level()) || !atOrAbove(rule.risk(), riskThreshold)) {
                    continue;
                }
                int from = 0;
                while ((from = text.indexOf(rule.term(), from)) >= 0) {
                    result.add(new Candidate(
                            rule.level(),
                            paragraph.getId(),
                            paragraph.getParagraphOrder(),
                            rule.category(),
                            rule.risk(),
                            from,
                            from + rule.term().length(),
                            rule.term(),
                            rule.replacement(),
                            rule.explanation(),
                            rule.basis(),
                            rule.confidence()));
                    from += rule.term().length();
                }
            }
            for (EffectiveReviewRuleProvider.Match match : effectiveRuleProvider.inspect(documentTypeCode, text)) {
                String level = levelForCategory(match.category());
                if (!enabledLevels.contains(level) || !atOrAbove(match.risk(), riskThreshold)) {
                    continue;
                }
                Candidate candidate = new Candidate(
                        level,
                        paragraph.getId(),
                        paragraph.getParagraphOrder(),
                        match.category(),
                        match.risk(),
                        match.start(),
                        match.end(),
                        match.original(),
                        // 内容安全类词条只提示，不提供自动替换（§21）
                        "L7".equals(level) ? null : match.replacement(),
                        match.explanation(),
                        match.basis(),
                        match.confidence());
                boolean duplicate = result.stream()
                        .anyMatch(existing -> java.util.Objects.equals(existing.paragraphId(), candidate.paragraphId())
                                && existing.start() == candidate.start()
                                && existing.end() == candidate.end()
                                && existing.original().equals(candidate.original())
                                && java.util.Objects.equals(existing.replacement(), candidate.replacement()));
                if (!duplicate) {
                    result.add(candidate);
                }
            }
            if (enabledLevels.contains("L3")) {
                inspectL3Paragraph(documentTypeCode, paragraph, text, riskThreshold, result);
            }
        }
        if (enabledLevels.contains("L3")) {
            inspectL3Document(documentTypeCode, paragraphs, riskThreshold, result);
            if (documentTitle != null
                    && !documentTitle.isBlank()
                    && List.of("通知", "通报", "报告", "请示", "批复", "函", "纪要", "意见", "决定", "公告", "通告")
                            .contains(documentTypeCode)
                    && !documentTitle.contains(documentTypeCode)
                    && atOrAbove("重要", riskThreshold)) {
                result.add(new Candidate(
                        "L3",
                        null,
                        1,
                        "公文规则",
                        "重要",
                        0,
                        documentTitle.length(),
                        documentTitle,
                        null,
                        "公文标题应包含能够反映行文性质的文种名称，请核实标题与所选文种是否一致。",
                        "党政机关公文标题由发文机关名称、事由和文种组成",
                        new BigDecimal("0.97")));
            }
        }
        if (enabledLevels.contains("L5")) addDeep(result, factInspector.inspect(paragraphs), riskThreshold);
        if (enabledLevels.contains("L6")) addDeep(result, logicInspector.inspect(paragraphs), riskThreshold);
        if (enabledLevels.contains("L7")) addDeep(result, safetyInspector.inspect(paragraphs), riskThreshold);
        return result.stream()
                .sorted(Comparator.comparingInt(Candidate::paragraphNumber).thenComparingInt(Candidate::start))
                .toList();
    }

    private void inspectL3Paragraph(
            String documentTypeCode,
            DocumentParagraphEntity paragraph,
            String text,
            String riskThreshold,
            List<Candidate> result) {
        Matcher placeholder = PLACEHOLDER.matcher(text);
        while (placeholder.find()) {
            addL3(
                    result,
                    paragraph,
                    placeholder.start(),
                    placeholder.end(),
                    placeholder.group(),
                    "重要",
                    riskThreshold,
                    "存在未填充的公文要素占位符，请补充实际内容。",
                    "输出模板占位符完整性规则");
        }
        if ("报告".equals(documentTypeCode)) {
            Matcher request = REQUEST_IN_REPORT.matcher(text);
            while (request.find()) {
                addL3(
                        result,
                        paragraph,
                        request.start(),
                        request.end(),
                        request.group(),
                        "重要",
                        riskThreshold,
                        "报告中不得夹带请示事项，确需请求批准的应另行制发请示。",
                        "党政机关公文处理工作条例文种适用规则");
            }
        }
    }

    private void inspectL3Document(
            String documentTypeCode,
            List<DocumentParagraphEntity> paragraphs,
            String riskThreshold,
            List<Candidate> result) {
        if (!"请示".equals(documentTypeCode) || paragraphs.isEmpty()) {
            return;
        }
        List<Occurrence> matters = new ArrayList<>();
        int totalLength = 0;
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            totalLength += text.length();
            Matcher matcher = REQUEST_MATTER.matcher(text);
            while (matcher.find()) {
                matters.add(new Occurrence(paragraph, matcher.start(), matcher.end(), matcher.group()));
            }
        }
        for (int index = 1; index < matters.size(); index++) {
            Occurrence occurrence = matters.get(index);
            addL3(
                    result,
                    occurrence.paragraph(),
                    occurrence.start(),
                    occurrence.end(),
                    occurrence.text(),
                    "重要",
                    riskThreshold,
                    "同一请示出现多个请求性事项，请核实是否违反一文一事要求。",
                    "请示应当一文一事");
        }
        DocumentParagraphEntity last = paragraphs.stream()
                .max(Comparator.comparing(DocumentParagraphEntity::getParagraphOrder))
                .orElse(paragraphs.get(paragraphs.size() - 1));
        String lastText = last.getPlainText() == null ? "" : last.getPlainText();
        if (totalLength >= 100 && !REQUEST_ENDING.matcher(lastText).find() && !lastText.isBlank()) {
            int start = Math.max(0, lastText.length() - Math.min(12, lastText.length()));
            addL3(
                    result,
                    last,
                    start,
                    lastText.length(),
                    lastText.substring(start),
                    "一般",
                    riskThreshold,
                    "请示结束语不规范，建议使用“妥否，请批示”等规范表述。",
                    "党政机关公文请示结束语规则");
        }
    }

    private void addL3(
            List<Candidate> result,
            DocumentParagraphEntity paragraph,
            int start,
            int end,
            String original,
            String risk,
            String riskThreshold,
            String explanation,
            String basis) {
        if (atOrAbove(risk, riskThreshold)) {
            result.add(new Candidate(
                    "L3",
                    paragraph.getId(),
                    paragraph.getParagraphOrder(),
                    "公文规则",
                    risk,
                    start,
                    end,
                    original,
                    null,
                    explanation,
                    basis,
                    new BigDecimal("0.99")));
        }
    }

    private void addDeep(
            List<Candidate> result,
            List<cn.gov.document.platform.review.application.deep.Finding> findings,
            String riskThreshold) {
        for (var finding : findings) {
            if (!atOrAbove(finding.risk(), riskThreshold)) continue;
            result.add(new Candidate(
                    finding.level(),
                    finding.paragraph().getId(),
                    finding.paragraph().getParagraphOrder(),
                    finding.category(),
                    finding.risk(),
                    finding.start(),
                    finding.end(),
                    finding.original(),
                    null,
                    finding.explanation(),
                    finding.basis(),
                    finding.confidence()));
        }
    }

    private String levelForCategory(String category) {
        if ("文字".equals(category)) return "L1";
        if ("规范".equals(category)) return "L2";
        if ("内容安全".equals(category) || "敏感词".equals(category)) return "L7";
        return "L3";
    }

    private boolean atOrAbove(String risk, String threshold) {
        List<String> order = List.of("提示", "一般", "重要", "严重");
        int actual = order.indexOf(risk);
        int minimum = order.indexOf(threshold);
        return actual >= (minimum < 0 ? 0 : minimum);
    }

    private record Rule(
            String level,
            String category,
            String risk,
            String term,
            String replacement,
            String explanation,
            String basis,
            BigDecimal confidence) {}

    private record Occurrence(DocumentParagraphEntity paragraph, int start, int end, String text) {}

    public record Candidate(
            String level,
            String paragraphId,
            int paragraphNumber,
            String category,
            String risk,
            int start,
            int end,
            String original,
            String replacement,
            String explanation,
            String basis,
            BigDecimal confidence) {}
}
