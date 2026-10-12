package cn.gov.document.platform.review.application.deep;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L7 政务内容安全审校的内置规则：个人敏感信息、密级标识、绝对化承诺表述。
 *
 * <p>单位自定义的敏感词通过词库维护（词条分类为“内容安全”或“敏感词”）。内容安全问题禁止自动修改，必须人工处理（§21）。
 */
public class ContentSafetyInspector {
    private static final String CATEGORY = "内容安全";
    private static final Pattern ID_NUMBER = Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern CLASSIFICATION = Pattern.compile("(绝密|机密|秘密)\\s*[★☆]");
    private static final List<String> ABSOLUTE_TERMS =
            List.of("万无一失", "零风险", "彻底根除", "永不反弹", "绝无问题", "百分之百解决", "100%解决");
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final String ID_CHECK = "10X98765432";

    public List<Finding> inspect(List<DocumentParagraphEntity> paragraphs) {
        List<Finding> result = new ArrayList<>();
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            Matcher id = ID_NUMBER.matcher(text);
            while (id.find()) {
                if (!validIdNumber(id.group())) continue;
                result.add(finding(
                        paragraph,
                        "严重",
                        id.start(),
                        id.end(),
                        id.group(),
                        "文稿包含居民身份证号码，属于敏感个人信息。除确有必要并经授权外，对外文稿应删除或脱敏处理。",
                        "《中华人民共和国个人信息保护法》第二十八条",
                        new BigDecimal("0.98")));
            }
            Matcher mobile = MOBILE.matcher(text);
            while (mobile.find()) {
                result.add(finding(
                        paragraph,
                        "提示",
                        mobile.start(),
                        mobile.end(),
                        mobile.group(),
                        "文稿包含手机号码。请确认是否为可公开的工作联系方式，否则应删除或脱敏。",
                        "《中华人民共和国个人信息保护法》",
                        new BigDecimal("0.80")));
            }
            Matcher classification = CLASSIFICATION.matcher(text);
            while (classification.find()) {
                result.add(finding(
                        paragraph,
                        "严重",
                        classification.start(),
                        classification.end(),
                        classification.group(),
                        "文稿含密级标识。涉密文稿禁止在本系统处理，请立即停止并按保密规定处置。",
                        "《中华人民共和国保守国家秘密法》；本系统保密控制要求（§32.3）",
                        new BigDecimal("0.97")));
            }
            for (String term : ABSOLUTE_TERMS) {
                int from = 0;
                while ((from = text.indexOf(term, from)) >= 0) {
                    result.add(finding(
                            paragraph,
                            "一般",
                            from,
                            from + term.length(),
                            term,
                            "绝对化或承诺性表述，对外发布可能形成不当承诺或引发舆情风险，建议改为客观、留有余地的表述。",
                            "政务公开表述风险规则",
                            new BigDecimal("0.85")));
                    from += term.length();
                }
            }
        }
        return result;
    }

    static boolean validIdNumber(String value) {
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (value.charAt(i) - '0') * ID_WEIGHTS[i];
        return Character.toUpperCase(value.charAt(17)) == ID_CHECK.charAt(sum % 11);
    }

    private static Finding finding(
            DocumentParagraphEntity paragraph,
            String risk,
            int start,
            int end,
            String original,
            String explanation,
            String basis,
            BigDecimal confidence) {
        return new Finding("L7", paragraph, CATEGORY, risk, start, end, original, explanation, basis, confidence);
    }
}
