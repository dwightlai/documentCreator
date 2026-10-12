package cn.gov.document.platform.review.application;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.review.application.deep.Finding;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L3 公文规则增强：按文种配置的行文方向检查语气，校验主送、抄送和附件说明（《党政机关公文处理工作条例》第十五条、第十六条）。
 */
class L3StructureInspector {
    static final Map<String, String> DEFAULT_DIRECTIONS = Map.ofEntries(
            Map.entry("请示", "上行"),
            Map.entry("报告", "上行"),
            Map.entry("通知", "下行"),
            Map.entry("通报", "下行"),
            Map.entry("批复", "下行"),
            Map.entry("决定", "下行"),
            Map.entry("意见", "下行"),
            Map.entry("命令", "下行"),
            Map.entry("决议", "下行"),
            Map.entry("公告", "下行"),
            Map.entry("通告", "下行"),
            Map.entry("函", "平行"));

    private static final String CATEGORY = "公文规则";
    private static final String REGULATION = "《党政机关公文处理工作条例》";
    private static final Pattern ORDER_TONE = Pattern.compile("责成|责令|限你|你们必须|务必于|立即执行");
    private static final Pattern PLEA_TONE = Pattern.compile("恳请|妥否[，,]?请批示|请予批准|请予批复");
    private static final Pattern ADDRESSEE = Pattern.compile("^([^：:，。；\\s]{2,40})[：:]\\s*$");
    private static final Pattern LEADER = Pattern.compile("书记|省长|市长|县长|区长|主任|部长|厅长|局长|同志");
    private static final Pattern COPY_TO = Pattern.compile("^抄送[：:]\\s*(.*)$");
    private static final Pattern SUBORDINATE = Pattern.compile("各(?:县|区|乡|镇|街道|部门|单位)|所属|下属|下级");
    private static final Pattern ATTACHMENT_LINE = Pattern.compile("^附件[：:]\\s*(.*)$");
    private static final Pattern ATTACHMENT_ITEM = Pattern.compile("(?:^|\\s)(\\d{1,2})[.．、]");
    private static final Pattern ATTACHMENT_REFERENCE = Pattern.compile("附件\\s*(\\d{1,2})");
    private static final Pattern ATTACHMENT_MENTION = Pattern.compile("见附件|详见附件|附件\\s*\\d");

    List<Finding> inspect(String documentTypeCode, String direction, List<DocumentParagraphEntity> paragraphs) {
        List<Finding> result = new ArrayList<>();
        List<Line> lines = lines(paragraphs);
        inspectTone(documentTypeCode, direction, lines, result);
        inspectAddressee(documentTypeCode, direction, lines, result);
        inspectCopyTo(documentTypeCode, lines, result);
        inspectAttachments(lines, result);
        return result;
    }

    private void inspectTone(String type, String direction, List<Line> lines, List<Finding> result) {
        if (direction == null) return;
        for (Line line : lines) {
            if ("下行".equals(direction)) {
                Matcher plea = PLEA_TONE.matcher(line.text());
                while (plea.find()) {
                    add(result, line, plea, "重要", type + "属于下行文，不应使用请示性用语。请核实文种或行文方向是否有误。", REGULATION + "第十四条文种适用规则");
                }
            } else if ("上行".equals(direction) || "平行".equals(direction)) {
                Matcher order = ORDER_TONE.matcher(line.text());
                while (order.find()) {
                    String explanation = "上行".equals(direction)
                            ? type + "属于上行文，应使用请求、报告语气，不宜使用指令性用语。"
                            : "函用于不相隶属机关之间商洽工作，应使用商洽语气，不宜使用指令性用语。";
                    add(result, line, order, "一般", explanation, REGULATION + "行文规则");
                }
            }
        }
    }

    /** 主送机关：正文开头单独成行、以冒号结尾的名称。 */
    private void inspectAddressee(String type, String direction, List<Line> lines, List<Finding> result) {
        Line first = lines.stream()
                .filter(line -> !line.text().isBlank())
                .findFirst()
                .orElse(null);
        if (first == null) return;
        Matcher addressee = ADDRESSEE.matcher(first.text().strip());
        if (!addressee.find()) return;
        String name = addressee.group(1);
        int offset = first.text().indexOf(name);
        if ("请示".equals(type) && (name.contains("、") || name.contains("，") || name.contains(","))) {
            result.add(
                    finding(first, offset, name, "重要", "请示应当主送一个上级机关，不得多头主送；需要同时送其他机关的，可用抄送形式。", REGULATION + "第十五条"));
        }
        Matcher leader = LEADER.matcher(name);
        if ("上行".equals(direction) && leader.find()) {
            result.add(
                    finding(first, offset, name, "重要", "除上级机关负责人直接交办事项外，不得以本机关名义向上级机关负责人报送公文。", REGULATION + "第十五条"));
        }
    }

    private void inspectCopyTo(String type, List<Line> lines, List<Finding> result) {
        for (Line line : lines) {
            Matcher copy = COPY_TO.matcher(line.text().strip());
            if (!copy.find()) continue;
            String recipients = copy.group(1).strip();
            int offset = line.text().indexOf(recipients);
            if ("请示".equals(type) && SUBORDINATE.matcher(recipients).find()) {
                result.add(finding(line, offset, recipients, "重要", "请示不得同时抄送下级机关。", REGULATION + "第十五条"));
            }
            if (!recipients.isEmpty() && !recipients.endsWith("。")) {
                result.add(
                        finding(line, offset, recipients, "提示", "抄送机关之间用逗号隔开，最后一个抄送机关名称后标句号。", "GB/T 9704—2012 版记要素"));
            }
        }
    }

    private void inspectAttachments(List<Line> lines, List<Finding> result) {
        int declared = 0;
        Line declaration = null;
        for (int index = 0; index < lines.size(); index++) {
            Matcher attachment = ATTACHMENT_LINE.matcher(lines.get(index).text().strip());
            if (!attachment.find()) continue;
            declaration = lines.get(index);
            List<Integer> numbers = new ArrayList<>();
            collectNumbers(attachment.group(1), numbers);
            for (int next = index + 1; next < lines.size(); next++) {
                String text = lines.get(next).text().strip();
                if (!text.matches("^\\d{1,2}[.．、].*")) break;
                collectNumbers(text, numbers);
            }
            declared = numbers.isEmpty() ? (attachment.group(1).isBlank() ? 0 : 1) : numbers.size();
            for (int i = 0; i < numbers.size(); i++) {
                if (numbers.get(i) != i + 1) {
                    Line line = declaration;
                    result.add(
                            finding(line, 0, line.text().strip(), "一般", "附件说明序号不连续，请核对附件顺序。", "GB/T 9704—2012 附件说明"));
                    break;
                }
            }
            break;
        }
        for (Line line : lines) {
            if (line == declaration) continue;
            Matcher reference = ATTACHMENT_REFERENCE.matcher(line.text());
            while (reference.find()) {
                int number = Integer.parseInt(reference.group(1));
                if (declaration != null && number > declared) {
                    add(
                            result,
                            line,
                            reference,
                            "重要",
                            "正文引用附件" + number + "，但附件说明只列出 " + declared + " 项。",
                            "GB/T 9704—2012 附件说明");
                }
            }
            Matcher mention = ATTACHMENT_MENTION.matcher(line.text());
            if (declaration == null && mention.find()) {
                add(result, line, mention, "一般", "正文提及附件，但未见“附件：”说明，请补充附件名称和序号。", "GB/T 9704—2012 附件说明");
            }
        }
    }

    private static void collectNumbers(String text, List<Integer> numbers) {
        Matcher item = ATTACHMENT_ITEM.matcher(text);
        while (item.find()) numbers.add(Integer.parseInt(item.group(1)));
    }

    private static void add(
            List<Finding> result, Line line, Matcher matcher, String risk, String explanation, String basis) {
        result.add(new Finding(
                "L3",
                line.paragraph(),
                CATEGORY,
                risk,
                line.offset() + matcher.start(),
                line.offset() + matcher.end(),
                matcher.group(),
                explanation,
                basis,
                new BigDecimal("0.90")));
    }

    private static Finding finding(
            Line line, int offset, String original, String risk, String explanation, String basis) {
        int start = line.offset() + Math.max(0, offset);
        return new Finding(
                "L3",
                line.paragraph(),
                CATEGORY,
                risk,
                start,
                start + original.length(),
                original,
                explanation,
                basis,
                new BigDecimal("0.92"));
    }

    private static List<Line> lines(List<DocumentParagraphEntity> paragraphs) {
        List<Line> lines = new ArrayList<>();
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            int offset = 0;
            for (String line : text.split("\n", -1)) {
                lines.add(new Line(paragraph, offset, line));
                offset += line.length() + 1;
            }
        }
        return lines;
    }

    private record Line(DocumentParagraphEntity paragraph, int offset, String text) {}
}
