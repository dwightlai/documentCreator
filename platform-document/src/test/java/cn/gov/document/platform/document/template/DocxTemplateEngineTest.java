package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.error.BusinessException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTemplateEngineTest {
    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    @Test
    void validatesAndRendersSplitRunPlaceholders() throws Exception {
        byte[] template = template(true);
        DocxTemplateEngine.Inspection inspection = engine.inspect("standard-request.docx", template);
        assertThat(inspection.errors()).isEmpty();
        assertThat(inspection.placeholders()).contains("{title}", "{recipient}", "{content}", "{date}");
        assertThat(inspection.fileHash()).hasSize(64);

        byte[] rendered = engine.render(template, Map.of(
                "title", "关于推进数字政务平台建设的请示",
                "recipient", "省人民政府：",
                "content", "为提升政务服务质效，现申请建设统一的数字政务平台。项目将坚持安全可控、分步实施。",
                "date", "2026年7月15日",
                "attachment_desc", "1. 项目建设方案"), List.of());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            String text = document.getParagraphs().stream().map(XWPFParagraph::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text)
                    .contains("关于推进数字政务平台建设的请示", "省人民政府：", "项目建设方案")
                    .doesNotContain("{title}", "{content}", "{#has_attachment}");
        }

        Path output = Path.of("target", "test-output", "template-render-sample.docx");
        Files.createDirectories(output.getParent());
        Files.write(output, rendered);
        assertThat(output).exists();
    }

    @Test
    void rejectsTemplateWithoutRequiredContentPlaceholder() throws Exception {
        byte[] template = template(false);
        DocxTemplateEngine.Inspection inspection = engine.inspect("invalid.docx", template);
        assertThat(inspection.errors()).anyMatch(value -> value.contains("正文必须使用"));
        assertThatThrownBy(() -> engine.render(template, Map.of("title", "测试"), List.of()))
                .isInstanceOf(BusinessException.class);
    }

    private byte[] template(boolean includeContent) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var pageSize = section.addNewPgSz();
            pageSize.setW(11906);
            pageSize.setH(16838);
            var margins = section.addNewPgMar();
            margins.setTop(2098);
            margins.setBottom(1985);
            margins.setLeft(1588);
            margins.setRight(1474);
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontFamily("SimSun");
            titleRun.setFontSize(22);
            titleRun.setText("{");
            title.createRun().setText("title}");

            XWPFParagraph recipient = document.createParagraph();
            recipient.createRun().setText("{recipient}");
            if (includeContent) {
                XWPFParagraph content = document.createParagraph();
                content.setFirstLineIndent(560);
                content.setSpacingBetween(1.6);
                content.createRun().setText("{content}");
            }
            document.createParagraph().createRun().setText("{#has_attachment}附件：{attachment_desc}{/has_attachment}");
            XWPFParagraph date = document.createParagraph();
            date.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            date.createRun().setText("{date}");
            document.write(output);
            return output.toByteArray();
        }
    }
}
