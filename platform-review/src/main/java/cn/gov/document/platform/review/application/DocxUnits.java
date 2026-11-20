package cn.gov.document.platform.review.application;

import cn.gov.document.platform.document.application.OfficialDocumentHead;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/** 审校引擎与原文展示共用的 DOCX 段落切分：版头单独标记，跨多段的标题合并为一段，段号两边一致。 */
final class DocxUnits {
    record Unit(
            String anchor,
            String kind,
            int number,
            String text,
            List<XWPFParagraph> paragraphs,
            XWPFTableRow row) {}

    private DocxUnits() {}

    static List<Unit> read(XWPFDocument word) {
        List<XWPFParagraph> all = word.getParagraphs();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) if (!all.get(i).getText().isBlank()) indexes.add(i);
        OfficialDocumentHead.Result head = OfficialDocumentHead.detect(indexes.stream()
                .map(i -> OfficialDocumentHead.Line.of(all.get(i)))
                .toList());
        var byParagraph = new java.util.IdentityHashMap<XWPFParagraph, Unit>();
        int number = 0;
        for (int k = 0; k < indexes.size(); k++) {
            int i = indexes.get(k);
            if (head.inTitle(k) && k != head.titleStart()) continue;
            number++;
            if (head.inTitle(k)) {
                List<XWPFParagraph> parts = indexes.subList(head.titleStart(), head.titleEnd()).stream()
                        .map(all::get)
                        .toList();
                byParagraph.put(all.get(i), new Unit("p-" + i, "title", number, head.title(), parts, null));
                number += head.titleEnd() - head.titleStart() - 1;
                continue;
            }
            byParagraph.put(all.get(i), new Unit(
                    "p-" + i, head.isHead(k) ? "head" : "paragraph", number, all.get(i).getText().trim(), List.of(all.get(i)), null));
        }
        var byRow = new java.util.IdentityHashMap<XWPFTableRow, Unit>();
        for (int t = 0; t < word.getTables().size(); t++) {
            var rows = word.getTables().get(t).getRows();
            for (int r = 0; r < rows.size(); r++) {
                XWPFTableRow row = rows.get(r);
                String value = row.getTableCells().stream().map(c -> c.getText()).collect(Collectors.joining("\t"));
                if (!value.isBlank())
                    byRow.put(row, new Unit("table-" + t + "-" + r, "tableRow", ++number, value, List.of(), row));
            }
        }
        List<Unit> result = new ArrayList<>();
        for (var element : word.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph && byParagraph.containsKey(paragraph))
                result.add(byParagraph.get(paragraph));
            else if (element instanceof XWPFTable table)
                for (var row : table.getRows()) if (byRow.containsKey(row)) result.add(byRow.get(row));
        }
        return result;
    }

    static String title(List<Unit> units) {
        return units.stream().filter(u -> "title".equals(u.kind())).map(Unit::text).findFirst().orElse(null);
    }
}
