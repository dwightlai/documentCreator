package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.storage.ObjectStorage;
import cn.gov.document.platform.document.api.DocumentDtos;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.review.domain.ReviewTaskEntity;
import cn.gov.document.platform.review.persistence.ReviewTaskMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewDocumentService {
    public record Block(
            String anchor,
            String kind,
            int paragraphNumber,
            String originalText,
            String currentText,
            boolean locked,
            BlockStyle style) {}

    public record BlockStyle(
            String alignment,
            int firstLineTwips,
            int leftTwips,
            int beforeTwips,
            int afterTwips,
            String fontFamily,
            int fontPt,
            boolean bold,
            boolean italic,
            String color) {}

    public record Content(
            String sourceSystem,
            String title,
            int reviewVersion,
            int currentVersion,
            boolean needsReview,
            List<Block> blocks) {}

    public record EditRequest(String anchor, String text, Integer expectedVersion) {}

    private final ReviewTaskMapper tasks;
    private final DocumentService documents;
    private final ObjectStorage storage;

    public ReviewDocumentService(ReviewTaskMapper tasks, DocumentService documents, ObjectStorage storage) {
        this.tasks = tasks;
        this.documents = documents;
        this.storage = storage;
    }

    public Content content(String taskId) {
        ReviewTaskEntity task = require(taskId);
        if ("upload".equals(task.getSourceSystem())) return uploadedContent(task);
        if (task.getWritingTaskId() == null) throw BusinessException.unprocessable("该审校记录没有可编辑的原文");
        DocumentDtos.DocumentResponse current = documents.get(task.getWritingTaskId());
        int reviewed = task.getDocumentVersion() == null ? current.currentVersion() : task.getDocumentVersion();
        DocumentDtos.DocumentResponse snapshot = documents.version(task.getWritingTaskId(), reviewed);
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block(
                "title",
                "title",
                0,
                task.getReviewTitle() == null ? snapshot.title() : task.getReviewTitle(),
                current.title(),
                false,
                null));
        for (var old : snapshot.paragraphs()) {
            var now = current.paragraphs().stream()
                    .filter(p -> p.paragraphOrder() == old.paragraphOrder())
                    .findFirst()
                    .orElse(null);
            blocks.add(new Block(
                    "paragraph-" + old.paragraphOrder(),
                    "paragraph",
                    old.paragraphOrder(),
                    old.plainText(),
                    now == null ? old.plainText() : now.plainText(),
                    now == null || now.locked(),
                    null));
        }
        return new Content(
                task.getSourceSystem(),
                current.title(),
                reviewed,
                current.currentVersion(),
                current.currentVersion() != reviewed,
                blocks);
    }

    @Transactional
    public Content edit(String taskId, EditRequest edit) {
        ReviewTaskEntity task = require(taskId);
        if (edit == null || edit.anchor() == null || edit.text() == null || edit.expectedVersion() == null)
            throw BusinessException.unprocessable("请提供定位、修改内容和当前版本");
        if (edit.text().isBlank() || edit.text().length() > 30000)
            throw BusinessException.unprocessable("修改内容不能为空，且不能超过 30000 字");
        if ("upload".equals(task.getSourceSystem())) return editUpload(task, edit);
        String writingId = task.getWritingTaskId();
        if (writingId == null) throw BusinessException.unprocessable("该审校记录没有可编辑的原文");
        var current = documents.get(writingId);
        if (current.currentVersion() != edit.expectedVersion()) throw BusinessException.conflict("原文已有新版本，请刷新后重试");
        if ("title".equals(edit.anchor())) {
            documents.renameForReview(writingId, edit.text().trim());
        } else {
            int order = paragraphOrder(edit.anchor());
            var paragraph = current.paragraphs().stream()
                    .filter(p -> p.paragraphOrder() == order)
                    .findFirst()
                    .orElseThrow(() -> BusinessException.notFound("段落不存在，请刷新原文"));
            if (paragraph.locked()) throw BusinessException.conflict("该段落已锁定");
            if (!Objects.equals(paragraph.plainText(), edit.text()))
                documents.updateParagraph(
                        writingId,
                        paragraph.id(),
                        new DocumentDtos.UpdateParagraphRequest(edit.text(), paragraph.lockVersion(), "审校中修改原文"));
        }
        return content(taskId);
    }

    public byte[] download(String taskId) {
        ReviewTaskEntity task = require(taskId);
        if (!"upload".equals(task.getSourceSystem())) throw BusinessException.unprocessable("智能写作文稿请在正文工作区导出 DOCX");
        return storage.read(task.getEditedFileId() == null ? task.getDocumentFileId() : task.getEditedFileId());
    }

    private Content uploadedContent(ReviewTaskEntity task) {
        List<DocxBlock> old = readBlocks(storage.read(task.getDocumentFileId()));
        List<DocxBlock> latest =
                task.getEditedFileId() == null ? old : readBlocks(storage.read(task.getEditedFileId()));
        List<Block> blocks = new ArrayList<>();
        for (DocxBlock original : old) {
            DocxBlock now = latest.stream()
                    .filter(b -> b.anchor.equals(original.anchor))
                    .findFirst()
                    .orElse(null);
            blocks.add(new Block(
                    original.anchor,
                    original.kind,
                    original.paragraphNumber,
                    original.text,
                    now == null ? original.text : now.text,
                    now == null,
                    now == null ? original.style : now.style));
        }
        int version = task.getEditVersion() == null ? 0 : task.getEditVersion();
        String title =
                blocks.isEmpty() ? task.getSourceFileName() : blocks.get(0).currentText();
        return new Content("upload", title, 0, version, version > 0, blocks);
    }

    private Content editUpload(ReviewTaskEntity task, EditRequest edit) {
        int version = task.getEditVersion() == null ? 0 : task.getEditVersion();
        if (version != edit.expectedVersion()) throw BusinessException.conflict("文件已有新版本，请刷新后重试");
        byte[] source =
                storage.read(task.getEditedFileId() == null ? task.getDocumentFileId() : task.getEditedFileId());
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(source));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<DocxBlock> blocks = blocks(word);
            DocxBlock target = blocks.stream()
                    .filter(b -> b.anchor.equals(edit.anchor()))
                    .findFirst()
                    .orElseThrow(() -> BusinessException.notFound("原文位置不存在，请刷新后重试"));
            if (target.paragraph != null) replaceText(target.paragraph, edit.text());
            else {
                String[] cells = edit.text().split("\\t", -1);
                if (cells.length != target.row.getTableCells().size())
                    throw BusinessException.unprocessable("表格行应保留原有列数，请用 Tab 分隔单元格");
                for (int i = 0; i < cells.length; i++) {
                    var cell = target.row.getCell(i);
                    if (cell.getParagraphs().isEmpty()) cell.addParagraph();
                    replaceText(cell.getParagraphs().get(0), cells[i]);
                    for (int j = 1; j < cell.getParagraphs().size(); j++)
                        replaceText(cell.getParagraphs().get(j), "");
                }
            }
            word.write(output);
            int next = version + 1;
            String key = RequestContext.tenantId() + "/review-edits/" + task.getId() + "/v" + next + "-"
                    + java.util.UUID.randomUUID() + ".docx";
            storage.put(
                    key,
                    output.toByteArray(),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            task.setEditedFileId(key);
            task.setEditVersion(next);
            if (tasks.updateById(task) != 1) throw BusinessException.conflict("文件已被其他人修改，请刷新后重试");
            return content(task.getId());
        } catch (IOException e) {
            throw BusinessException.unprocessable("DOCX 文件无法编辑");
        }
    }

    private static void replaceText(XWPFParagraph paragraph, String text) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            paragraph.createRun().setText(text);
            return;
        }
        String old = paragraph.getText();
        List<String> chunks = runs.stream()
                .map(run -> run.getText(0) == null ? "" : run.getText(0))
                .toList();
        if (chunks.stream().allMatch(value -> !value.isEmpty())
                && runs.stream().allMatch(run -> run.getCTR().sizeOfTArray() == 1)
                && String.join("", chunks).equals(old)) {
            int prefix = 0;
            while (prefix < old.length() && prefix < text.length() && old.charAt(prefix) == text.charAt(prefix))
                prefix++;
            int suffix = 0;
            while (suffix < old.length() - prefix
                    && suffix < text.length() - prefix
                    && old.charAt(old.length() - 1 - suffix) == text.charAt(text.length() - 1 - suffix)) suffix++;
            int end = old.length() - suffix;
            String inserted = text.substring(prefix, text.length() - suffix);
            int position = 0;
            boolean insertedOnce = false;
            for (int i = 0; i < runs.size(); i++) {
                String chunk = chunks.get(i);
                int next = position + chunk.length();
                String before =
                        position < prefix ? chunk.substring(0, Math.min(chunk.length(), prefix - position)) : "";
                String after = next > end ? chunk.substring(Math.max(0, end - position)) : "";
                if (!insertedOnce && (next > prefix || i == runs.size() - 1)) {
                    before += inserted;
                    insertedOnce = true;
                }
                runs.get(i).setText(before + after, 0);
                position = next;
            }
            return;
        }
        // 包含复杂域、换行或特殊 XML 的段落无法按 run 定位，保留段落属性和首个 run 的样式。
        runs.get(0).setText(text, 0);
        for (int i = 1; i < runs.size(); i++) runs.get(i).setText("", 0);
    }

    private record DocxBlock(
            String anchor,
            String kind,
            int paragraphNumber,
            String text,
            XWPFParagraph paragraph,
            XWPFTableRow row,
            BlockStyle style) {}

    private static List<DocxBlock> readBlocks(byte[] bytes) {
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return blocks(word).stream()
                    .map(b -> new DocxBlock(b.anchor, b.kind, b.paragraphNumber, b.text, null, null, b.style))
                    .toList();
        } catch (IOException e) {
            throw BusinessException.unprocessable("DOCX 文件无法读取");
        }
    }

    // 展示采用 DOCX 原始顺序；段落编号仍与审校引擎的“正文段落、表格行”顺序一致。
    private static List<DocxBlock> blocks(XWPFDocument word) {
        var paragraphs = new java.util.IdentityHashMap<XWPFParagraph, DocxBlock>();
        var rowsByTable = new java.util.IdentityHashMap<XWPFTableRow, DocxBlock>();
        int number = 0;
        for (int i = 0; i < word.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = word.getParagraphs().get(i);
            String value = paragraph.getText().trim();
            if (!value.isBlank()) {
                number++;
                paragraphs.put(
                        paragraph,
                        new DocxBlock(
                                "p-" + i,
                                number == 1 ? "title" : "paragraph",
                                number,
                                value,
                                paragraph,
                                null,
                                style(paragraph)));
            }
        }
        for (int t = 0; t < word.getTables().size(); t++) {
            var rows = word.getTables().get(t).getRows();
            for (int r = 0; r < rows.size(); r++) {
                XWPFTableRow row = rows.get(r);
                String value = row.getTableCells().stream()
                        .map(c -> c.getText())
                        .collect(java.util.stream.Collectors.joining("\t"));
                if (!value.isBlank())
                    rowsByTable.put(
                            row, new DocxBlock("table-" + t + "-" + r, "tableRow", ++number, value, null, row, null));
            }
        }
        List<DocxBlock> result = new ArrayList<>();
        for (var element : word.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph && paragraphs.containsKey(paragraph))
                result.add(paragraphs.get(paragraph));
            else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable table)
                for (var row : table.getRows()) if (rowsByTable.containsKey(row)) result.add(rowsByTable.get(row));
        }
        return result;
    }

    private static BlockStyle style(XWPFParagraph paragraph) {
        XWPFRun run = paragraph.getRuns().stream()
                .filter(item -> item.getText(0) != null && !item.getText(0).isBlank())
                .findFirst()
                .orElse(null);
        String alignment = paragraph.getAlignment() == null
                ? "LEFT"
                : paragraph.getAlignment().name();
        return new BlockStyle(
                alignment,
                paragraph.getIndentationFirstLine(),
                paragraph.getIndentationLeft(),
                paragraph.getSpacingBefore(),
                paragraph.getSpacingAfter(),
                run == null ? null : run.getFontFamily(),
                run == null ? 0 : run.getFontSize(),
                run != null && run.isBold(),
                run != null && run.isItalic(),
                run == null ? null : run.getColor());
    }

    private static int paragraphOrder(String anchor) {
        if (anchor == null || !anchor.startsWith("paragraph-")) throw BusinessException.unprocessable("原文定位无效");
        try {
            return Integer.parseInt(anchor.substring("paragraph-".length()));
        } catch (NumberFormatException e) {
            throw BusinessException.unprocessable("原文定位无效");
        }
    }

    private ReviewTaskEntity require(String taskId) {
        ReviewTaskEntity task = tasks.selectById(taskId);
        if (task == null) throw BusinessException.notFound("审校记录不存在");
        return task;
    }
}
