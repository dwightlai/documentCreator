package cn.gov.document.platform.document.application;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

@Component
public class DocxExporter {
    public byte[] export(DocumentEntity document, List<DocumentParagraphEntity> paragraphs) {
        try (XWPFDocument word = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = word.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText(document.getTitle());
            titleRun.setBold(true);
            titleRun.setFontFamily("方正小标宋简体");
            titleRun.setFontSize(22);
            for (DocumentParagraphEntity item : paragraphs) {
                XWPFParagraph paragraph = word.createParagraph();
                paragraph.setFirstLineIndent(840);
                paragraph.setSpacingBetween(1.75);
                XWPFRun run = paragraph.createRun();
                run.setText(item.getPlainText() == null ? "" : item.getPlainText());
                run.setFontFamily("仿宋_GB2312");
                run.setFontSize(16);
            }
            word.getProperties().getCustomProperties().addProperty("ai_assisted", "true");
            word.getProperties().getCustomProperties().addProperty("document_version", document.getCurrentVersion());
            word.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("DOCX 导出失败", exception);
        }
    }
}
