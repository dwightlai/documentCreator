package cn.gov.document.platform.document.application;

import java.util.List;
import org.springframework.util.StringUtils;

public final class DocumentTypeDetector {
    private static final List<String> TYPES = List.of("通报", "批复", "决定", "请示", "报告", "通知", "纪要", "函");

    private DocumentTypeDetector() {}

    public static String resolve(String code, String text) {
        if (StringUtils.hasText(code)) return code.trim();
        String detected = fromText(text);
        return detected == null ? "通知" : detected;
    }

    public static String fromText(String text) {
        if (!StringUtils.hasText(text)) return null;
        return fromLines(List.of(text.strip().split("\\n+")));
    }

    public static String fromLines(List<String> lines) {
        List<String> head = lines.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(15)
                .toList();
        for (String line : head) {
            String type = titleType(line);
            if (type != null) return type;
        }
        StringBuilder title = new StringBuilder();
        for (String line : head) {
            if (line.endsWith("：") || line.endsWith(":")) break;
            if (line.matches(".*[〔\\[]\\d{4}[〕\\]].*")) continue;
            title.append(line);
        }
        return titleType(title.toString());
    }

    private static String titleType(String text) {
        if (!StringUtils.hasText(text) || text.length() > 120) return null;
        for (String type : TYPES) {
            if (text.endsWith("的" + type)) return type;
        }
        if (text.endsWith("会议纪要")) return "纪要";
        return null;
    }
}
