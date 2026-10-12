package cn.gov.document.platform.review.application.deep;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L5 事实与数据审校的确定性部分（§22.2）：分项合计、变化率、比例合计、日期合法性与区间、同一指标前后一致性。
 *
 * <p>只做可复算的检查，计算过程写入问题说明；不确定的情况标记为“待人工核实”，不给出替换文本。
 */
public class FactDataInspector {
    private static final String CATEGORY = "事实数据";
    private static final String CALC_BASIS = "确定性计算校验（§22.2）";
    private static final String COUNT_UNIT = "(项|个|家|人|件|次|元|所|座|条|台|套|名|户)";
    private static final Pattern TOTAL =
            Pattern.compile("(?:共计|合计|总计|累计|共)\\p{IsHan}{0,8}?\\s*" + NumberText.NUMBER + "\\s*" + COUNT_UNIT);
    private static final Pattern ITEM = Pattern.compile(NumberText.NUMBER + "\\s*" + COUNT_UNIT);
    private static final Pattern SHARES =
            Pattern.compile("分别占[^\\d%％。；;]{0,12}((?:\\d+(?:\\.\\d+)?\\s*[%％]\\s*[、，,和及与]?\\s*){2,})");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[%％]");
    private static final Pattern DATE = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern MONTH = Pattern.compile("(\\d{4})年(\\d{1,2})月(?!\\d)");
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(?:至|到|—|－|-|~)\\s*(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern METRIC =
            Pattern.compile("(?:共计|累计|达到|完成|共|达|为)\\s*" + NumberText.NUMBER + "\\s*" + COUNT_UNIT);
    private static final String CHANGE_CONTEXT = "增减较比降升超";

    public List<Finding> inspect(List<DocumentParagraphEntity> paragraphs) {
        List<Finding> result = new ArrayList<>();
        Map<String, MetricValue> metrics = new HashMap<>();
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            inspectTotals(paragraph, text, result);
            inspectChangeRates(paragraph, text, result);
            inspectShares(paragraph, text, result);
            inspectDates(paragraph, text, result);
            inspectMetricConsistency(paragraph, text, metrics, result);
        }
        return result;
    }

    private void inspectTotals(DocumentParagraphEntity paragraph, String text, List<Finding> result) {
        Matcher total = TOTAL.matcher(text);
        while (total.find()) {
            int sentenceEnd = sentenceEnd(text, total.end());
            int detailStart = text.indexOf("其中", total.end());
            if (detailStart < 0 || detailStart >= sentenceEnd) continue;
            BigDecimal expected = NumberText.parse(total.group(1), total.group(2));
            String unit = total.group(3);
            List<BigDecimal> parts = new ArrayList<>();
            Matcher item = ITEM.matcher(text).region(detailStart + 2, sentenceEnd);
            while (item.find()) {
                if (!unit.equals(item.group(3)) || followsChangeWord(text, item.start())) continue;
                parts.add(NumberText.parse(item.group(1), item.group(2)));
            }
            if (parts.size() < 2) continue;
            BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            int comparison = sum.compareTo(expected);
            if (comparison == 0) continue;
            String process =
                    String.join(" + ", parts.stream().map(NumberText::format).toList()) + " = " + NumberText.format(sum)
                            + unit;
            if (comparison > 0) {
                result.add(finding(
                        paragraph,
                        "重要",
                        total.start(),
                        total.end(),
                        total.group(),
                        "分项合计大于总数：" + process + "，总数为 " + NumberText.format(expected) + unit + "。请核对总数或分项数据。",
                        new BigDecimal("0.93")));
            } else {
                result.add(finding(
                        paragraph,
                        "提示",
                        total.start(),
                        total.end(),
                        total.group(),
                        "待人工核实：已列分项合计 " + process + "，小于总数 " + NumberText.format(expected) + unit
                                + "。如分项未穷尽可忽略，否则请核对数据。",
                        new BigDecimal("0.70")));
            }
        }
    }

    private void inspectChangeRates(DocumentParagraphEntity paragraph, String text, List<Finding> result) {
        for (ChangeStatement change : ChangeStatement.find(text)) {
            BigDecimal actual = change.actualRate();
            if (actual == null) continue;
            BigDecimal stated = change.signedStatedRate();
            // 方向矛盾由 L6 逻辑审校报告，这里只校验数值大小
            if (actual.signum() != stated.signum()) continue;
            BigDecimal tolerance = stated.abs().multiply(new BigDecimal("0.01")).max(new BigDecimal("0.1"));
            if (actual.subtract(stated).abs().compareTo(tolerance.add(new BigDecimal("0.05"))) <= 0) continue;
            result.add(finding(
                    paragraph,
                    "重要",
                    change.start(),
                    change.end(),
                    change.text(),
                    "变化率与数值不符：(" + NumberText.format(change.to()) + " − " + NumberText.format(change.from())
                            + ") ÷ " + NumberText.format(change.from()) + " × 100% = "
                            + NumberText.format(actual.abs()) + "%，正文为 "
                            + NumberText.format(change.statedRate().abs())
                            + "%。请核对基数、现值或变化率。",
                    new BigDecimal("0.95")));
        }
    }

    private void inspectShares(DocumentParagraphEntity paragraph, String text, List<Finding> result) {
        Matcher shares = SHARES.matcher(text);
        while (shares.find()) {
            List<BigDecimal> values = new ArrayList<>();
            Matcher percent = PERCENT.matcher(shares.group(1));
            while (percent.find()) values.add(new BigDecimal(percent.group(1)));
            BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(new BigDecimal("100.5")) <= 0) continue;
            result.add(finding(
                    paragraph,
                    "重要",
                    shares.start(),
                    shares.end(),
                    shares.group(),
                    "各部分占比合计为 " + NumberText.format(sum) + "%，超过 100%。请核对各项比例。",
                    new BigDecimal("0.92")));
        }
    }

    private void inspectDates(DocumentParagraphEntity paragraph, String text, List<Finding> result) {
        List<int[]> reported = new ArrayList<>();
        Matcher date = DATE.matcher(text);
        while (date.find()) {
            if (toDate(date.group(1), date.group(2), date.group(3)) != null) continue;
            reported.add(new int[] {date.start(), date.end()});
            result.add(finding(
                    paragraph, "重要", date.start(), date.end(), date.group(), "日期不存在，请核对年月日。", new BigDecimal("0.99")));
        }
        Matcher month = MONTH.matcher(text);
        while (month.find()) {
            int value = Integer.parseInt(month.group(2));
            if (value >= 1 && value <= 12 || overlaps(reported, month.start())) continue;
            result.add(finding(
                    paragraph, "重要", month.start(), month.end(), month.group(), "月份不存在，请核对。", new BigDecimal("0.99")));
        }
        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            LocalDate from = toDate(range.group(1), range.group(2), range.group(3));
            String endYear = range.group(4) == null ? range.group(1) : range.group(4);
            LocalDate to = toDate(endYear, range.group(5), range.group(6));
            if (from == null || to == null || !to.isBefore(from)) continue;
            result.add(finding(
                    paragraph,
                    "重要",
                    range.start(),
                    range.end(),
                    range.group(),
                    "日期区间的结束日期（" + to + "）早于开始日期（" + from + "），请核对。",
                    new BigDecimal("0.97")));
        }
    }

    private void inspectMetricConsistency(
            DocumentParagraphEntity paragraph, String text, Map<String, MetricValue> metrics, List<Finding> result) {
        Matcher metric = METRIC.matcher(text);
        while (metric.find()) {
            String subject = precedingSubject(text, metric.start());
            if (subject.length() < 3) continue;
            String unit = metric.group(3);
            BigDecimal value = NumberText.parse(metric.group(1), metric.group(2));
            MetricValue previous =
                    metrics.putIfAbsent(subject + "|" + unit, new MetricValue(value, paragraph.getParagraphOrder()));
            if (previous == null || previous.value().compareTo(value) == 0) continue;
            int start = metric.start() - subject.length();
            result.add(finding(
                    paragraph,
                    "提示",
                    start,
                    metric.end(),
                    text.substring(start, metric.end()),
                    "待人工核实：第 " + previous.paragraphNumber() + " 段“" + subject + "”为 "
                            + NumberText.format(previous.value()) + unit + "，此处为 " + NumberText.format(value) + unit
                            + "。如统计口径不同请补充说明。",
                    new BigDecimal("0.65")));
        }
    }

    /** 指标主语：紧邻动词之前的连续汉字，最多 6 个。 */
    private static String precedingSubject(String text, int verbStart) {
        int start = verbStart;
        while (start > 0
                && verbStart - start < 6
                && Character.UnicodeScript.of(text.charAt(start - 1)) == Character.UnicodeScript.HAN) start--;
        return text.substring(start, verbStart);
    }

    private static Finding finding(
            DocumentParagraphEntity paragraph,
            String risk,
            int start,
            int end,
            String original,
            String explanation,
            BigDecimal confidence) {
        return new Finding("L5", paragraph, CATEGORY, risk, start, end, original, explanation, CALC_BASIS, confidence);
    }

    private static int sentenceEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '；' || c == ';' || c == '\n') return i;
        }
        return text.length();
    }

    private static boolean followsChangeWord(String text, int position) {
        String before = text.substring(Math.max(0, position - 3), position);
        return before.chars().anyMatch(c -> CHANGE_CONTEXT.indexOf(c) >= 0);
    }

    private static boolean overlaps(List<int[]> spans, int position) {
        return spans.stream().anyMatch(span -> position >= span[0] && position < span[1]);
    }

    private static LocalDate toDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private record MetricValue(BigDecimal value, int paragraphNumber) {}
}
