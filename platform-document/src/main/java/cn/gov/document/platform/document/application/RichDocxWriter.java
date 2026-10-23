package cn.gov.document.platform.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * 把一个正文段落写成 Word 内容：纯文本写成一个 Word 段落，富文本按块写出段落、列表（以编号文字前缀表示，与纯文本推导规则一致）和表格。
 * 写到哪里、用什么基础格式由 {@link Target} 决定：普通导出追加到文末，套红排版插入到模板正文位置并沿用模板格式。
 */
public final class RichDocxWriter {
    /** 内容写入位置与基础格式。 */
    public interface Target {
        XWPFParagraph newParagraph();

        /** 新建表格（至少一行一格），行列数由写入器补齐。 */
        XWPFTable newTable();

        /**
         * @param level 列表嵌套层级，0 表示普通段落
         * @param cell 是否位于表格单元格内
         */
        void style(XWPFParagraph paragraph, int level, boolean cell);

        XWPFRun newRun(XWPFParagraph paragraph, boolean cell);
    }

    private final Target target;
    private final Consumer<XWPFParagraph> onFirst;
    private XWPFParagraph first;

    /** @param onFirst 写出第一个 Word 段落时回调（导出时用于挂来源批注起点），可为空 */
    public RichDocxWriter(Target target, Consumer<XWPFParagraph> onFirst) {
        this.target = target;
        this.onFirst = onFirst;
    }

    /** 写出的第一个 Word 段落；什么都没写时为空。 */
    public XWPFParagraph first() {
        return first;
    }

    public void plain(String text) {
        XWPFParagraph paragraph = start(target.newParagraph(), 0, false);
        target.newRun(paragraph, false).setText(text == null ? "" : text);
    }

    public void rich(JsonNode doc) {
        for (JsonNode block : doc.path("content")) {
            if ("table".equals(block.path("type").asText())) table(block);
            else block(block, 0, "", target::newParagraph, false, false);
        }
    }

    private void block(
            JsonNode node, int level, String prefix, Supplier<XWPFParagraph> factory, boolean cell, boolean bold) {
        switch (node.path("type").asText()) {
            case "paragraph" -> {
                XWPFParagraph paragraph = start(factory.get(), level, cell);
                if (!prefix.isEmpty()) target.newRun(paragraph, cell).setText(prefix);
                for (JsonNode child : node.path("content")) {
                    XWPFRun run = target.newRun(paragraph, cell);
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
        int columns =
                rows.stream().mapToInt(row -> row.path("content").size()).max().orElse(0);
        if (rows.isEmpty() || columns == 0) return;
        XWPFTable table = target.newTable();
        resize(table, rows.size(), columns);
        table.setWidth("100%");
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        for (int r = 0; r < rows.size(); r++) {
            JsonNode cells = rows.get(r).path("content");
            for (int c = 0; c < cells.size(); c++) {
                XWPFTableCell cell = table.getRow(r).getCell(c);
                boolean header = "tableHeader".equals(cells.get(c).path("type").asText());
                int[] used = {0};
                Supplier<XWPFParagraph> factory =
                        () -> used[0]++ == 0 && !cell.getParagraphs().isEmpty()
                                ? cell.getParagraphs().get(0)
                                : cell.addParagraph();
                for (JsonNode block : cells.get(c).path("content")) block(block, 0, "", factory, true, header);
            }
        }
    }

    /** 新建表格的行列数因创建方式而异，统一补齐到目标行列。 */
    private static void resize(XWPFTable table, int rows, int columns) {
        if (table.getNumberOfRows() == 0) table.createRow();
        XWPFTableRow head = table.getRow(0);
        while (head.getTableCells().size() < columns) head.addNewTableCell();
        while (table.getNumberOfRows() < rows) table.createRow();
        for (XWPFTableRow row : table.getRows()) while (row.getTableCells().size() < columns) row.addNewTableCell();
    }

    private XWPFParagraph start(XWPFParagraph paragraph, int level, boolean cell) {
        target.style(paragraph, level, cell);
        if (first == null) {
            first = paragraph;
            if (onFirst != null) onFirst.accept(paragraph);
        }
        return paragraph;
    }
}
