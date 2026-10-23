package cn.gov.document.platform.document.application;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFComments;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

@Component
public class DocxExporter {
    private static final String COMMENT_AUTHOR = "政文智枢·内容来源";

    public byte[] export(DocumentEntity document, List<DocumentParagraphEntity> paragraphs) {
        return export(document, paragraphs, null, false);
    }

    /**
     * §33 双轨标识：AI 参与生成的段落附加批注说明来源类型和材料 ID；文档自定义属性写入 AI 生成占比、模型版本和生成记录数。
     *
     * @param disclosure 为 null 时不写入 AI 标识属性
     * @param sourceComments 是否为 AI 参与生成的段落添加来源批注
     */
    public byte[] export(
            DocumentEntity document,
            List<DocumentParagraphEntity> paragraphs,
            DocumentService.AiDisclosure disclosure,
            boolean sourceComments) {
        try (XWPFDocument word = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = word.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText(document.getTitle());
            titleRun.setBold(true);
            titleRun.setFontFamily("方正小标宋简体");
            titleRun.setFontSize(22);
            XWPFComments comments = sourceComments ? word.createComments() : null;
            int commentId = 0;
            for (DocumentParagraphEntity item : paragraphs) {
                String note = comments == null ? null : sourceNote(item);
                BigInteger id = BigInteger.valueOf(commentId);
                BodyWriter body = new BodyWriter(word, note == null ? null : id);
                if (item.getRichContent() == null) body.plain(item.getPlainText());
                else body.rich(RichContent.parse(item.getRichContent()));
                XWPFParagraph paragraph = body.first;
                if (note != null && paragraph != null) {
                    paragraph.getCTP().addNewCommentRangeEnd().setId(id);
                    paragraph.createRun().getCTR().addNewCommentReference().setId(id);
                    XWPFComment comment = comments.createComment(id);
                    comment.setAuthor(COMMENT_AUTHOR);
                    comment.createParagraph().createRun().setText(note);
                    commentId++;
                }
            }
            var properties = word.getProperties().getCustomProperties();
            properties.addProperty("ai_assisted", "true");
            properties.addProperty("document_version", document.getCurrentVersion());
            if (disclosure != null) {
                properties.addProperty(
                        "ai_generated_ratio", disclosure.aiGeneratedRatio().toPlainString());
                properties.addProperty("model_versions", String.join(",", disclosure.modelVersions()));
                properties.addProperty("generation_records_count", String.valueOf(disclosure.generationRecordsCount()));
            }
            word.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("DOCX 导出失败", exception);
        }
    }

    /** 把一个正文段落写入 DOCX：纯文本段落写成一个 Word 段落；富文本按块写出段落、列表和表格。来源批注挂在写出的第一个 Word 段落上。 */
    private static final class BodyWriter {
        private static final int BODY_SIZE = 16;
        private static final int CELL_SIZE = 14;
        private final XWPFDocument word;
        private final BigInteger commentId;
        private XWPFParagraph first;

        BodyWriter(XWPFDocument word, BigInteger commentId) {
            this.word = word;
            this.commentId = commentId;
        }

        void plain(String text) {
            XWPFParagraph paragraph = start(word.createParagraph(), 0, false);
            run(paragraph, BODY_SIZE).setText(text == null ? "" : text);
        }

        void rich(JsonNode doc) {
            for (JsonNode block : doc.path("content")) {
                if ("table".equals(block.path("type").asText())) table(block);
                else block(block, 0, "", word::createParagraph, false, false);
            }
        }

        private void block(
                JsonNode node, int level, String prefix, Supplier<XWPFParagraph> factory, boolean cell, boolean bold) {
            switch (node.path("type").asText()) {
                case "paragraph" -> {
                    XWPFParagraph paragraph = start(factory.get(), level, cell);
                    int size = cell ? CELL_SIZE : BODY_SIZE;
                    if (!prefix.isEmpty()) run(paragraph, size).setText(prefix);
                    for (JsonNode child : node.path("content")) {
                        XWPFRun run = run(paragraph, size);
                        if ("hardBreak".equals(child.path("type").asText())) {
                            run.addBreak();
                            continue;
                        }
                        run.setText(child.path("text").asText());
                        if (bold || RichContent.hasMark(child, "bold")) run.setBold(true);
                        if (RichContent.hasMark(child, "italic")) run.setItalic(true);
                        if (RichContent.hasMark(child, "underline")) run.setUnderline(UnderlinePatterns.SINGLE);
                    }
                }
                case "bulletList", "orderedList" -> {
                    boolean ordered = "orderedList".equals(node.path("type").asText());
                    int number = node.path("attrs").path("start").asInt(1);
                    int index = 0;
                    for (JsonNode item : node.path("content")) {
                        String itemPrefix = ordered ? (number + index) + ". " : "• ";
                        int child = 0;
                        for (JsonNode itemBlock : item.path("content"))
                            block(itemBlock, level + 1, child++ == 0 ? itemPrefix : "", factory, cell, bold);
                        index++;
                    }
                }
                default -> {}
            }
        }

        private void table(JsonNode node) {
            List<JsonNode> rows = new ArrayList<>();
            node.path("content").forEach(rows::add);
            int columns = rows.stream().mapToInt(row -> row.path("content").size()).max().orElse(0);
            if (rows.isEmpty() || columns == 0) return;
            XWPFTable table = word.createTable(rows.size(), columns);
            table.setWidth("100%");
            for (int r = 0; r < rows.size(); r++) {
                JsonNode cells = rows.get(r).path("content");
                for (int c = 0; c < cells.size(); c++) {
                    XWPFTableCell cell = table.getRow(r).getCell(c);
                    boolean header = "tableHeader".equals(cells.get(c).path("type").asText());
                    int[] used = {0};
                    Supplier<XWPFParagraph> factory =
                            () -> used[0]++ == 0 ? cell.getParagraphs().get(0) : cell.addParagraph();
                    for (JsonNode block : cells.get(c).path("content")) block(block, 0, "", factory, true, header);
                }
            }
        }

        private XWPFParagraph start(XWPFParagraph paragraph, int level, boolean cell) {
            if (cell) {
                paragraph.setSpacingBetween(1.2);
            } else {
                paragraph.setFirstLineIndent(840);
                if (level > 1) paragraph.setIndentationLeft(420 * (level - 1));
                paragraph.setSpacingBetween(1.75);
            }
            if (first == null) {
                first = paragraph;
                if (commentId != null) paragraph.getCTP().addNewCommentRangeStart().setId(commentId);
            }
            return paragraph;
        }

        private static XWPFRun run(XWPFParagraph paragraph, int size) {
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("仿宋_GB2312");
            run.setFontSize(size);
            return run;
        }
    }

    /** 只为 AI 参与的段落添加批注；人工录入段落不打标，避免干扰正式文稿阅读。 */
    private static String sourceNote(DocumentParagraphEntity paragraph) {
        String origin = paragraph.getContentOrigin();
        if (origin == null || "人工录入".equals(origin)) return null;
        String materials = paragraph.getSourceMaterialIds() == null
                        || paragraph.getSourceMaterialIds().isBlank()
                        || "[]".equals(paragraph.getSourceMaterialIds())
                ? "无"
                : paragraph.getSourceMaterialIds().replaceAll("[\\[\\]\"]", "");
        return "来源类型：" + origin + "；证据层级：" + paragraph.getContentSourceLevel() + "；引用材料：" + materials;
    }
}
