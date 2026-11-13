package cn.gov.document.platform.review.application;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Business classification is independent from the engine's execution levels. */
public final class ReviewCategory {
    public static final String POLITICAL = "政治与重要表述";
    public static final String FACT = "政策法规与事实依据";
    public static final String DOCUMENT = "公文业务规范";
    public static final String LOGIC = "内容与逻辑";
    public static final String LANGUAGE = "语言文字";
    public static final String FORMAT = "格式与版式";
    public static final List<String> ALL = List.of(POLITICAL, FACT, DOCUMENT, LOGIC, LANGUAGE, FORMAT);

    private ReviewCategory() {}

    public static String of(String category) {
        if (ALL.contains(category)) return category;
        return switch (category == null ? "" : category) {
            case "文字", "错别字", "标点符号", "规范", "规范用语", "词库" -> LANGUAGE;
            case "公文格式", "版式核验", "格式版式", "数字用法" -> FORMAT;
            case "行文规则", "文种选用", "保密要求", "公文规则", "内容安全", "敏感词" -> DOCUMENT;
            case "内容审核", "逻辑语义", "逻辑" -> LOGIC;
            case "事实数据", "政策法规" -> FACT;
            case "政治表述" -> POLITICAL;
            default -> throw new IllegalArgumentException("未定义审校业务分类: " + category);
        };
    }

    public static Set<String> levels(List<String> categories) {
        return categories.stream()
                .flatMap(category -> switch (category) {
                    case POLITICAL -> List.of("L7").stream();
                    case FACT -> List.of("L5").stream();
                    case DOCUMENT -> List.of("L3", "L7").stream();
                    case FORMAT -> List.of("L3", "L4").stream();
                    case LOGIC -> List.of("L6").stream();
                    case LANGUAGE -> List.of("L1", "L2").stream();
                    default -> throw new IllegalArgumentException("未定义审校业务分类: " + category);
                })
                .collect(Collectors.toSet());
    }
}
