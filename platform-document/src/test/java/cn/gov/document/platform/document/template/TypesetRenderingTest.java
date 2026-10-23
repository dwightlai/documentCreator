package cn.gov.document.platform.document.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

class TypesetRenderingTest {
    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    private static final String RICH =
            """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"一、"},{"type":"text","text":"总体要求","marks":[{"type":"bold"}]}]},
              {"type":"table","content":[{"type":"tableRow","content":[
                {"type":"tableHeader","content":[{"type":"paragraph","content":[{"type":"text","text":"项目"}]}]},
                {"type":"tableHeader","content":[{"type":"paragraph","content":[{"type":"text","text":"金额"}]}]}]}]}]}
            """;

    @Test
    void builtInTemplatePassesInspectionWithOnlyStandardPlaceholders() {
        DocxTemplateEngine.Inspection inspection = engine.inspect("standard.docx", StandardOfficialTemplate.build());
        assertThat(inspection.errors()).isEmpty();
        assertThat(inspection.placeholders())
                .contains("{sender}", "{doc_number}", "{title}", "{recipient}", "{content}", "{date}", "{issue_unit}");
    }

    @Test
    void expandsBodyIntoSeparateParagraphsKeepingSlotFormattingAndRichContent() throws Exception {
        byte[] rendered = engine.renderDocument(
                StandardOfficialTemplate.build(),
                Map.of(
                        "sender", "示范市人民政府文件",
                        "doc_number", "示政〔2026〕18号",
                        "title", "关于申请专项建设资金的请示",
                        "recipient", "省人民政府：",
                        "date", "2026年10月23日",
                        "issue_unit", "示范市人民政府办公室",
                        "issue_date", "2026年10月23日"),
                List.of(
                        new DocxTemplateEngine.BodyBlock("第一段正文。\n第二段正文。", null),
                        new DocxTemplateEngine.BodyBlock("一、总体要求\n项目\t金额", RICH)));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();
            int recipient = texts.indexOf("省人民政府：");
            assertThat(texts.subList(recipient + 1, recipient + 4)).containsExactly("第一段正文。", "第二段正文。", "一、总体要求");
            XWPFParagraph first = document.getParagraphs().get(recipient + 1);
            assertThat(first.getFirstLineIndent()).isEqualTo(640);
            assertThat(first.getRuns().get(0).getFontFamily()).isEqualTo("仿宋_GB2312");
            assertThat(document.getParagraphs().get(recipient + 3).getRuns())
                    .anyMatch(run -> "总体要求".equals(run.text()) && run.isBold());
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().get(0).getRow(0).getCell(1).getText())
                    .isEqualTo("金额");
            // 表格位于正文位置（版记之前），未填写的抄送与签发人不留残余文字
            String all = String.join("\n", texts);
            assertThat(all).contains("示政〔2026〕18号").doesNotContain("签发人", "抄送", "{");
            assertThat(all).contains("示范市人民政府办公室\t2026年10月23日印发");
        }

        Path output = Path.of("target", "test-output", "typeset-standard-sample.docx");
        Files.createDirectories(output.getParent());
        Files.write(output, rendered);
    }

    @Test
    void conditionalSignerAndCopyToAppearWhenFilled() throws Exception {
        byte[] rendered = engine.renderDocument(
                StandardOfficialTemplate.build(),
                Map.of("title", "关于报送材料的报告", "doc_number", "示政〔2026〕19号", "signer", "张某", "copy_to", "市发展改革委"),
                List.of(new DocxTemplateEngine.BodyBlock("正文。", null)));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            String all = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(all).contains("示政〔2026〕19号　　签发人：张某", "抄送：市发展改革委。");
        }
    }
}
