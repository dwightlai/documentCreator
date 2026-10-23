package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.HtmlUtils;

/**
 * 段落富文本：编辑器提交的 ProseMirror JSON 文档。只接受公文正文需要的白名单节点（段落、列表、表格）和格式（加粗、倾斜、下划线），
 * 并推导出审校、字数统计和冲突比对使用的纯文本：块之间以换行分隔，列表项带编号前缀，表格单元格以制表符分隔（与导入 DOCX 的表格文本一致）。
 */
public final class RichContent {
    public static final int MAX_LENGTH = 200_000;
    private static final int MAX_DEPTH = 16;
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline");
    private static final Set<String> BLOCKS = Set.of("paragraph", "bulletList", "orderedList");
    private static final Map<String, Set<String>> CHILDREN = Map.of(
            "doc", Set.of("paragraph", "bulletList", "orderedList", "table"),
            "paragraph", Set.of("text", "hardBreak"),
            "bulletList", Set.of("listItem"),
            "orderedList", Set.of("listItem"),
            "listItem", BLOCKS,
            "table", Set.of("tableRow"),
            "tableRow", Set.of("tableCell", "tableHeader"),
            "tableCell", BLOCKS,
            "tableHeader", BLOCKS);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RichContent() {}

    /** 解析并校验富文本，不合法时返回 422。 */
    public static JsonNode parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw invalid("格式不正确");
        }
        if (root == null || !"doc".equals(root.path("type").asText())) throw invalid("缺少文档根节点");
        validate(root, 0);
        return root;
    }

    public static String plainText(JsonNode doc) {
        TextWriter writer = new TextWriter();
        writer.blocks(doc, "\n", "");
        return writer.out.toString().strip();
    }

    /**
     * 把纯文本修改（如采纳审校建议）作用回富文本：改动落在同一文本节点内时保留原有格式；
     * 跨越格式边界、涉及换行或无法验证结果时返回 null，由调用方降级为纯文本段落。
     */
    public static String rebase(String richJson, String oldPlain, String newPlain) {
        JsonNode doc;
        try {
            doc = parse(richJson);
        } catch (BusinessException invalidStored) {
            return null;
        }
        TextWriter writer = new TextWriter();
        writer.blocks(doc, "\n", "");
        String full = writer.out.toString();
        if (!full.strip().equals(oldPlain)) return null;
        int lead = full.length() - full.stripLeading().length();
        int max = Math.min(oldPlain.length(), newPlain.length());
        int prefix = 0;
        while (prefix < max && oldPlain.charAt(prefix) == newPlain.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < max - prefix
                && oldPlain.charAt(oldPlain.length() - 1 - suffix) == newPlain.charAt(newPlain.length() - 1 - suffix))
            suffix++;
        String insert = newPlain.substring(prefix, newPlain.length() - suffix);
        if (insert.indexOf('\n') >= 0 || insert.indexOf('\t') >= 0) return null;
        int from = lead + prefix;
        int to = lead + oldPlain.length() - suffix;
        for (TextSpan span : writer.spans) {
            if (span.start() > from || to > span.end()) continue;
            String text = span.node().path("text").asText();
            String replaced = text.substring(0, from - span.start()) + insert + text.substring(to - span.start());
            if (replaced.isEmpty()) return null; // 编辑器不接受空文本节点
            span.node().put("text", replaced);
            String json = doc.toString();
            return plainText(doc).equals(newPlain) ? json : null;
        }
        return null;
    }

    /** 渲染为 HTML 片段（已转义），供 HTML 导出使用。 */
    public static String toHtml(JsonNode doc) {
        StringBuilder html = new StringBuilder();
        htmlChildren(doc, html);
        return html.toString();
    }

    public static boolean hasMark(JsonNode text, String mark) {
        for (JsonNode item : text.path("marks")) if (mark.equals(item.path("type").asText())) return true;
        return false;
    }

    private static void validate(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw invalid("嵌套层级过深");
        String type = node.path("type").asText();
        if ("text".equals(type)) {
            if (!node.path("text").isTextual() || node.path("text").asText().isEmpty()) throw invalid("文本节点缺少内容");
            for (JsonNode mark : node.path("marks")) {
                String name = mark.path("type").asText();
                if (!MARKS.contains(name)) throw invalid("不支持的格式：" + name);
            }
            return;
        }
        if ("hardBreak".equals(type)) return;
        Set<String> allowed = CHILDREN.get(type);
        if (allowed == null) throw invalid("不支持的内容：" + type);
        JsonNode content = node.path("content");
        if (!content.isMissingNode() && !content.isArray()) throw invalid("节点内容格式不正确");
        for (JsonNode child : content) {
            String childType = child.path("type").asText();
            if (!allowed.contains(childType)) throw invalid(type + " 中不允许出现 " + childType);
            validate(child, depth + 1);
        }
    }

    private static BusinessException invalid(String reason) {
        return BusinessException.unprocessable("段落富文本不合法：" + reason);
    }

    private static void htmlChildren(JsonNode node, StringBuilder html) {
        for (JsonNode child : node.path("content")) htmlNode(child, html);
    }

    private static void htmlNode(JsonNode node, StringBuilder html) {
        String tag = switch (node.path("type").asText()) {
            case "paragraph" -> "p";
            case "bulletList" -> "ul";
            case "orderedList" -> "ol";
            case "listItem" -> "li";
            case "table" -> "table";
            case "tableRow" -> "tr";
            case "tableCell" -> "td";
            case "tableHeader" -> "th";
            case "hardBreak" -> "br";
            default -> null;
        };
        if ("text".equals(node.path("type").asText())) {
            String text = HtmlUtils.htmlEscape(node.path("text").asText());
            if (hasMark(node, "bold")) text = "<strong>" + text + "</strong>";
            if (hasMark(node, "italic")) text = "<em>" + text + "</em>";
            if (hasMark(node, "underline")) text = "<u>" + text + "</u>";
            html.append(text);
            return;
        }
        if (tag == null) return;
        if ("br".equals(tag)) {
            html.append("<br>");
            return;
        }
        html.append('<').append(tag);
        int start = node.path("attrs").path("start").asInt(1);
        if ("ol".equals(tag) && start != 1) html.append(" start=\"").append(start).append('"');
        html.append('>');
        htmlChildren(node, html);
        html.append("</").append(tag).append('>');
    }

    private record TextSpan(ObjectNode node, int start, int end) {}

    private static final class TextWriter {
        private final StringBuilder out = new StringBuilder();
        private final List<TextSpan> spans = new ArrayList<>();

        void blocks(JsonNode container, String separator, String firstPrefix) {
            int index = 0;
            for (JsonNode child : container.path("content")) {
                if (index > 0) out.append(separator);
                block(child, index == 0 ? firstPrefix : "");
                index++;
            }
        }

        private void block(JsonNode node, String prefix) {
            switch (node.path("type").asText()) {
                case "paragraph" -> {
                    out.append(prefix);
                    inline(node);
                }
                case "bulletList", "orderedList" -> {
                    boolean ordered = "orderedList".equals(node.path("type").asText());
                    int number = node.path("attrs").path("start").asInt(1);
                    int index = 0;
                    for (JsonNode item : node.path("content")) {
                        if (index > 0) out.append('\n');
                        blocks(item, "\n", ordered ? (number + index) + ". " : "• ");
                        index++;
                    }
                }
                case "table" -> {
                    int row = 0;
                    for (JsonNode tableRow : node.path("content")) {
                        if (row++ > 0) out.append('\n');
                        int cell = 0;
                        for (JsonNode tableCell : tableRow.path("content")) {
                            if (cell++ > 0) out.append('\t');
                            blocks(tableCell, " ", "");
                        }
                    }
                }
                default -> {}
            }
        }

        private void inline(JsonNode paragraph) {
            for (JsonNode child : paragraph.path("content")) {
                if ("hardBreak".equals(child.path("type").asText())) {
                    out.append('\n');
                } else if (child instanceof ObjectNode text) {
                    int start = out.length();
                    out.append(text.path("text").asText());
                    spans.add(new TextSpan(text, start, out.length()));
                }
            }
        }
    }
}
