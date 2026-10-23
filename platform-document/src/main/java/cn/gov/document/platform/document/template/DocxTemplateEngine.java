package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import cn.gov.document.platform.document.application.RichContent;
import cn.gov.document.platform.document.application.RichDocxWriter;
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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
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
            "{/has_attachment}",
            "{#has_copy_to}",
            "{/has_copy_to}",
            "{#has_signer}",
            "{/has_signer}");
    /** 独占一段的正文占位符：套红排版时按段展开为多个 Word 段落。 */
    private static final Set<String> BODY_SLOTS = Set.of("{content}", "{#sections}{paragraph}{/sections}");

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
        return fill(template, document -> {}, values, sections);
    }

    /** 套红排版的一段正文：富文本为空时按纯文本写入（多行文字拆成多个 Word 段落）。 */
    public record BodyBlock(String plainText, String richContent) {}

    /**
     * 套红排版：正文写到模板中独占一段的正文占位符位置，每段生成独立的 Word 段落并沿用占位段落的段落与字体格式，保留加粗、列表和表格；
     * 其余占位符按普通规则替换。模板的正文占位符与其他文字同处一段时，退回按纯文本合并写入。
     */
    public byte[] renderDocument(byte[] template, Map<String, String> values, List<BodyBlock> body) {
        List<String> plain = body.stream().map(BodyBlock::plainText).toList();
        Map<String, String> merged = new java.util.HashMap<>(values);
        merged.put("content", String.join(System.lineSeparator(), plain));
        return fill(template, document -> expandBody(document, body), merged, plain);
    }

    private void expandBody(XWPFDocument document, List<BodyBlock> body) {
        XWPFParagraph slot = document.getParagraphs().stream()
                .filter(paragraph -> BODY_SLOTS.contains(paragraph.getText().strip()))
                .findFirst()
                .orElse(null);
        if (slot == null) return;
        SlotTarget target = new SlotTarget(document, slot);
        for (BodyBlock block : body) {
            if (block.richContent() != null) {
                new RichDocxWriter(target, null).rich(RichContent.parse(block.richContent()));
                continue;
            }
            for (String line : (block.plainText() == null ? "" : block.plainText()).split("\\R"))
                if (!line.isBlank()) new RichDocxWriter(target, null).plain(line.strip());
        }
        document.removeBodyElement(document.getPosOfParagraph(slot));
    }

    /** 插入到模板正文占位段落之前，复制其段落格式（缩进、行距）与首个文字块的字体格式。 */
    private static final class SlotTarget implements RichDocxWriter.Target {
        private final XWPFDocument document;
        private final XWPFParagraph slot;
        private final CTPPr paragraphFormat;
        private final CTRPr runFormat;

        SlotTarget(XWPFDocument document, XWPFParagraph slot) {
            this.document = document;
            this.slot = slot;
            this.paragraphFormat = slot.getCTP().isSetPPr() ? slot.getCTP().getPPr() : null;
            this.runFormat = slot.getRuns().isEmpty()
                    ? null
                    : slot.getRuns().get(0).getCTR().getRPr();
        }

        @Override
        public XWPFParagraph newParagraph() {
            XmlCursor cursor = slot.getCTP().newCursor();
            try {
                return document.insertNewParagraph(cursor);
            } finally {
                cursor.dispose();
            }
        }

        @Override
        public XWPFTable newTable() {
            XmlCursor cursor = slot.getCTP().newCursor();
            try {
                return document.insertNewTbl(cursor);
            } finally {
                cursor.dispose();
            }
        }

        @Override
        public void style(XWPFParagraph paragraph, int level, boolean cell) {
            if (cell) {
                paragraph.setSpacingBetween(1.0);
                return;
            }
            if (paragraphFormat != null) paragraph.getCTP().setPPr((CTPPr) paragraphFormat.copy());
            if (level > 1) paragraph.setIndentationLeft(420 * (level - 1));
        }

        @Override
        public XWPFRun newRun(XWPFParagraph paragraph, boolean cell) {
            XWPFRun run = paragraph.createRun();
            if (runFormat != null) run.getCTR().setRPr((CTRPr) runFormat.copy());
            if (cell) run.setFontSize(14);
            return run;
        }
    }

    private byte[] fill(
            byte[] template, Consumer<XWPFDocument> prepare, Map<String, String> values, List<String> sections) {
        Inspection inspection = inspect("template.docx", template);
        if (!inspection.errors().isEmpty()) {
            throw BusinessException.unprocessable(String.join("；", inspection.errors()));
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            prepare.accept(document);
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
        rendered = renderConditional(rendered, "has_copy_to", hasText(values.get("copy_to")));
        rendered = renderConditional(rendered, "has_signer", hasText(values.get("signer")));
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
