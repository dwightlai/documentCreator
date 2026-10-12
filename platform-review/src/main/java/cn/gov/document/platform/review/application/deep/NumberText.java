package cn.gov.document.platform.review.application.deep;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 正文中阿拉伯数字的解析与展示：支持千分位逗号和“万”“亿”数量级。 */
final class NumberText {
    /** 数值、可选数量级；调用方在其后拼接单位。 */
    static final String NUMBER = "(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(万|亿)?";

    private NumberText() {}

    static BigDecimal parse(String digits, String magnitude) {
        BigDecimal value = new BigDecimal(digits.replace(",", ""));
        if ("万".equals(magnitude)) return value.multiply(BigDecimal.valueOf(10_000));
        if ("亿".equals(magnitude)) return value.multiply(BigDecimal.valueOf(100_000_000));
        return value;
    }

    static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
