package cn.gov.document.platform.review.application.deep;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L6 逻辑审校中可由规则确定的部分（§23）：增减方向与数值矛盾、层次序号不连续。
 *
 * <p>语义层面的矛盾和逻辑跳跃由 {@link ModelLogicReviewer} 借助模型提出，再经原文校验后交人工确认。
 */
public class LogicInspector {
    private static final String CATEGORY = "逻辑语义";
    private static final String NUMERALS = "一二三四五六七八九十";
    private static final Pattern LEVEL_ONE = Pattern.compile("^([一二三四五六七八九十]{1,3})、");
    private static final Pattern LEVEL_TWO = Pattern.compile("^[（(]([一二三四五六七八九十]{1,3})[）)]");
    private static final Pattern LEVEL_THREE = Pattern.compile("^(\\d{1,2})[.．、](?!\\d)");

    public List<Finding> inspect(List<DocumentParagraphEntity> paragraphs) {
        List<Finding> result = new ArrayList<>();
        Integer[] previous = new Integer[3];
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            inspectDirections(paragraph, text, result);
            inspectNumbering(paragraph, text, previous, result);
        }
        return result;
    }

    private void inspectDirections(DocumentParagraphEntity paragraph, String text, List<Finding> result) {
        for (ChangeStatement change : ChangeStatement.find(text)) {
            int actual = change.to().compareTo(change.from());
            if (actual == 0) continue;
            String actualWord = actual > 0 ? "增加" : "减少";
            boolean movementContradicts = ChangeStatement.isDecrease(change.movementVerb()) == (actual > 0);
            boolean rateContradicts = ChangeStatement.isDecrease(change.rateVerb()) == (actual > 0)
                    || change.statedRate().signum() < 0;
            if (!movementContradicts && !rateContradicts) continue;
            result.add(new Finding(
                    "L6",
                    paragraph,
                    CATEGORY,
                    "重要",
                    change.start(),
                    change.end(),
                    change.text(),
                    "变化方向与数值矛盾：数值由 " + NumberText.format(change.from()) + " 变为 " + NumberText.format(change.to())
                            + "，实际为" + actualWord + "，但表述为“"
                            + (movementContradicts ? change.movementVerb() : change.rateVerb())
                            + "”。建议核对数据或调整表述方向。",
                    "逻辑一致性校验（§23）",
                    new BigDecimal("0.95")));
        }
    }

    /** 三级层次序号（“一、”“（一）”“1.”）应连续；上级序号出现时重置下级计数。 */
    private void inspectNumbering(
            DocumentParagraphEntity paragraph, String text, Integer[] previous, List<Finding> result) {
        int offset = 0;
        for (String line : text.split("\n", -1)) {
            int indent = line.length() - line.stripLeading().length();
            String trimmed = line.stripLeading();
            Pattern[] levels = {LEVEL_ONE, LEVEL_TWO, LEVEL_THREE};
            for (int level = 0; level < levels.length; level++) {
                Matcher matcher = levels[level].matcher(trimmed);
                if (!matcher.find()) continue;
                Integer number = level == 2 ? Integer.valueOf(matcher.group(1)) : chineseNumber(matcher.group(1));
                if (number == null) break;
                Integer last = previous[level];
                boolean broken = last == null ? number != 1 : number != last + 1;
                if (broken) {
                    String expected = last == null ? "1" : String.valueOf(last + 1);
                    result.add(new Finding(
                            "L6",
                            paragraph,
                            CATEGORY,
                            last == null ? "提示" : "一般",
                            offset + indent + matcher.start(),
                            offset + indent + matcher.end(),
                            matcher.group(),
                            "层次序号不连续：此处应为第 " + expected + " 项，实际为第 " + number + " 项。请检查是否遗漏或多余层次。",
                            "党政机关公文结构层次序数规范",
                            new BigDecimal("0.90")));
                }
                previous[level] = number;
                for (int lower = level + 1; lower < previous.length; lower++) previous[lower] = null;
                break;
            }
            offset += line.length() + 1;
        }
    }

    private static Integer chineseNumber(String value) {
        if (value.length() == 1) return NUMERALS.indexOf(value) + 1;
        int ten = value.indexOf('十');
        if (ten < 0) return null;
        int tens = ten == 0 ? 1 : NUMERALS.indexOf(value.charAt(0)) + 1;
        int ones = ten == value.length() - 1 ? 0 : NUMERALS.indexOf(value.charAt(ten + 1)) + 1;
        return tens * 10 + ones;
    }
}
