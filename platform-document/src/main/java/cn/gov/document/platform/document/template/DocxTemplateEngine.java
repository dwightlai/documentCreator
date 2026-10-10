package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

@Component
public class DocxTemplateEngine {
    private static final int MAX_TEMPLATE_BYTES = 20 * 1024 * 1024;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(?:#|/)?[a-z][a-z0-9_]*}");
    private static final Set<String> SUPPORTED = Set.of(
            "{title}",
            "{recipient}",
            "{sender}",
            "{doc_number}",
            "{signer}",
            "{content}",
            "{closing}",
            "{attachment_desc}",
            "{copy_to}",
            "{issue_unit}",
            "{issue_date}",
            "{date}",
            "{#sections}",
            "{paragraph}",
            "{/sections}",
            "{#has_attachment}",
            "{/has_attachment}");

    public Inspection inspect(String fileName, byte[] bytes) {
        DocxSecurityValidator.validate(fileName, bytes, MAX_TEMPLATE_BYTES, "DOCX 模板");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            Set<String> placeholders = new LinkedHashSet<>();
            for (XWPFParagraph paragraph : allParagraphs(document)) {
                Matcher matcher = PLACEHOLDER.matcher(paragraph.getText());
                while (matcher.find()) {
                    placeholders.add(matcher.group());
                }
            }
            List<String> errors = new ArrayList<>();
            if (!placeholders.contains("{title}")) {
                errors.add("缺少必填占位符 {title}");
            }
            boolean singleContent = placeholders.contains("{content}");
            boolean sectionContent = placeholders.containsAll(Set.of("{#sections}", "{paragraph}", "{/sections}"));
            if (!singleContent && !sectionContent) {
                errors.add("正文必须使用 {content} 或完整的 {#sections}{paragraph}{/sections} 循环区域");
            }
            placeholders.stream()
                    .filter(value -> !SUPPORTED.contains(value))
                    .forEach(value -> errors.add("不支持的占位符 " + value));
            return new Inspection(List.copyOf(placeholders), List.copyOf(errors), sha256(bytes));
        } catch (IOException | RuntimeException invalidDocx) {
            if (invalidDocx instanceof BusinessException businessException) {
                throw businessException;
            }
            throw BusinessException.unprocessable("DOCX 模板无法解析或文件已损坏");
        }
    }

    public byte[] render(byte[] template, Map<String, String> values, List<String> sections) {
        Inspection inspection = inspect("template.docx", template);
        if (!inspection.errors().isEmpty()) {
            throw BusinessException.unprocessable(String.join("；", inspection.errors()));
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (XWPFParagraph paragraph : allParagraphs(document)) {
                String text = paragraph.getText();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                String rendered = renderText(text, values, sections);
                if (!rendered.equals(text)) {
                    replaceParagraphText(paragraph, rendered);
                }
            }
            document.getProperties().getCoreProperties().setCreator("智能公文写作平台");
            document.getProperties().getCoreProperties().setLastModifiedByUser("智能公文写作平台");
            document.write(output);
            byte[] result = output.toByteArray();
            if (containsPlaceholder(result)) {
                throw BusinessException.unprocessable("模板渲染后仍存在未替换占位符");
            }
            return result;
        } catch (IOException invalidDocx) {
            throw BusinessException.unprocessable("DOCX 模板渲染失败");
        }
    }

    private String renderText(String text, Map<String, String> values, List<String> sections) {
        String rendered = text;
        String loop = "{#sections}{paragraph}{/sections}";
        if (rendered.contains(loop)) {
            rendered = rendered.replace(
                    loop, String.join(System.lineSeparator(), sections == null ? List.of() : sections));
        }
        for (String placeholder : SUPPORTED) {
            if (placeholder.startsWith("{#") || placeholder.startsWith("{/")) {
                continue;
            }
            String key = placeholder.substring(1, placeholder.length() - 1);
            rendered = rendered.replace(placeholder, values.getOrDefault(key, ""));
        }
        boolean hasAttachment = values.containsKey("attachment_desc")
                && values.get("attachment_desc") != null
                && !values.get("attachment_desc").isBlank();
        rendered = renderConditional(rendered, "has_attachment", hasAttachment);
        return rendered;
    }

    private String renderConditional(String value, String name, boolean visible) {
        String start = "{#" + name + "}";
        String end = "{/" + name + "}";
        int startIndex = value.indexOf(start);
        int endIndex = value.indexOf(end);
        if (startIndex < 0 && endIndex < 0) {
            return value;
        }
        if (startIndex < 0 || endIndex < startIndex) {
            throw BusinessException.unprocessable("模板条件区域不完整: " + name);
        }
        String content = value.substring(startIndex + start.length(), endIndex);
        return value.substring(0, startIndex) + (visible ? content : "") + value.substring(endIndex + end.length());
    }

    private void replaceParagraphText(XWPFParagraph paragraph, String value) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            paragraph.createRun().setText(value);
            return;
        }
        runs.get(0).setText(value, 0);
        for (int index = runs.size() - 1; index > 0; index--) {
            paragraph.removeRun(index);
        }
    }

    private List<XWPFParagraph> allParagraphs(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = new ArrayList<>();
        collect(document.getBodyElements(), paragraphs);
        document.getHeaderList().forEach(header -> collect(header.getBodyElements(), paragraphs));
        document.getFooterList().forEach(footer -> collect(footer.getBodyElements(), paragraphs));
        return paragraphs;
    }

    private void collect(List<IBodyElement> elements, List<XWPFParagraph> target) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                target.add(paragraph);
            } else if (element instanceof XWPFTable table) {
                table.getRows()
                        .forEach(row -> row.getTableCells().forEach(cell -> collect(cell.getBodyElements(), target)));
            }
        }
    }

    private boolean containsPlaceholder(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return allParagraphs(document).stream()
                    .map(XWPFParagraph::getText)
                    .anyMatch(text -> PLACEHOLDER.matcher(text).find());
        } catch (IOException exception) {
            return true;
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record Inspection(List<String> placeholders, List<String> errors, String fileHash) {}
}
