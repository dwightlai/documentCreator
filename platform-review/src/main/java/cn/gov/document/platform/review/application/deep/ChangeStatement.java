package cn.gov.document.platform.review.application.deep;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别“由 A 增长到 B，增长 P%”一类变化表述，供事实计算（L5）与方向一致性（L6）共用。
 *
 * <p>首个数值带百分号的表述（如“由35%提高到42%”）属于百分点变化，不在此计算。
 */
record ChangeStatement(
        int start,
        int end,
        String text,
        BigDecimal from,
        BigDecimal to,
        String movementVerb,
        String rateVerb,
        BigDecimal statedRate) {

    private static final String UNIT = "(?:万元|亿元|元|项|个|家|人|件|次|所|座|条|台|套|名|户|吨|千瓦时|公里|平方米|亩)?";
    private static final String VERB = "(增长|增加|提高|上升|提升|下降|减少|降低|下滑|回落)";
    private static final Set<String> DECREASE = Set.of("下降", "减少", "降低", "下滑", "回落");
    private static final Pattern PATTERN =
            Pattern.compile("(?:由|从)\\s*" + NumberText.NUMBER + "\\s*" + UNIT + "\\s*" + VERB
                    + "(?:到|至)\\s*" + NumberText.NUMBER + "\\s*" + UNIT + "[^。；;\\d]{0,6}?" + VERB
                    + "(?:了)?\\s*(-?\\d+(?:\\.\\d+)?)\\s*[%％]");

    static List<ChangeStatement> find(String text) {
        List<ChangeStatement> result = new ArrayList<>();
        Matcher m = PATTERN.matcher(text);
        while (m.find()) {
            result.add(new ChangeStatement(
                    m.start(),
                    m.end(),
                    m.group(),
                    NumberText.parse(m.group(1), m.group(2)),
                    NumberText.parse(m.group(4), m.group(5)),
                    m.group(3),
                    m.group(6),
                    new BigDecimal(m.group(7))));
        }
        return result;
    }

    static boolean isDecrease(String verb) {
        return DECREASE.contains(verb);
    }

    /** 实际变化率（百分数），起始值为 0 时无法计算返回 null。 */
    BigDecimal actualRate() {
        if (from.signum() == 0) return null;
        return to.subtract(from).multiply(BigDecimal.valueOf(100)).divide(from, MathContext.DECIMAL64);
    }

    /** 按文字方向带符号的陈述变化率。 */
    BigDecimal signedStatedRate() {
        return isDecrease(rateVerb) ? statedRate.abs().negate() : statedRate;
    }
}
