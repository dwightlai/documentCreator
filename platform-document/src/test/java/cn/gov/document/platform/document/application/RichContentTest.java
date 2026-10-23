package cn.gov.document.platform.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class RichContentTest {
    static final String RICH =
            """
            {"type":"doc","content":[
              {"type":"paragraph","content":[
                {"type":"text","text":"一、"},
                {"type":"text","text":"总体要求","marks":[{"type":"bold"}]},
                {"type":"text","text":"必须落实。","marks":[{"type":"underline"}]}]},
              {"type":"orderedList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"加强组织领导"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"强化资金保障"}]}]}]},
              {"type":"table","content":[
                {"type":"tableRow","content":[
                  {"type":"tableHeader","content":[{"type":"paragraph","content":[{"type":"text","text":"项目"}]}]},
                  {"type":"tableHeader","content":[{"type":"paragraph","content":[{"type":"text","text":"金额"}]}]}]},
                {"type":"tableRow","content":[
                  {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"道路改造"}]}]},
                  {"type":"tableCell","content":[{"type":"paragraph"}]}]}]}]}
            """;

    @Test
    void derivesPlainTextWithListNumbersAndTabSeparatedCells() {
        assertThat(RichContent.plainText(RichContent.parse(RICH)))
                .isEqualTo("一、总体要求必须落实。\n1. 加强组织领导\n2. 强化资金保障\n项目\t金额\n道路改造");
    }

    @Test
    void rejectsNodesAndMarksOutsideTheWhitelist() {
        assertThatThrownBy(() -> RichContent.parse(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"http://x\"}}]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("image");
        assertThatThrownBy(
                        () -> RichContent.parse(
                                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\"}]}]}]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("link");
        assertThatThrownBy(() -> RichContent.parse("not json")).isInstanceOf(BusinessException.class);
    }

    @Test
    void rebaseKeepsFormattingWhenChangeStaysInsideOneTextNode() {
        String old = RichContent.plainText(RichContent.parse(RICH));
        String fixed = old.replace("总体要求", "总体目标");
        String rebased = RichContent.rebase(RICH, old, fixed);
        assertThat(rebased).isNotNull();
        assertThat(RichContent.plainText(RichContent.parse(rebased))).isEqualTo(fixed);
        assertThat(rebased).contains("{\"type\":\"text\",\"text\":\"总体目标\",\"marks\":[{\"type\":\"bold\"}]}");
    }

    @Test
    void rebaseGivesUpWhenChangeCrossesFormattingOrStructure() {
        String old = RichContent.plainText(RichContent.parse(RICH));
        assertThat(RichContent.rebase(RICH, old, old.replace("要求必须", "任务应当"))).isNull();
        assertThat(RichContent.rebase(RICH, old, old.replace("1. 加强", "加强"))).isNull();
        assertThat(RichContent.rebase(RICH, "过期的旧文本", "新文本")).isNull();
    }

    @Test
    void docxExportWritesRunsListsAndTables() throws Exception {
        DocumentEntity document = new DocumentEntity();
        document.setTitle("关于专项资金的请示");
        document.setCurrentVersion(3);
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setPlainText(RichContent.plainText(RichContent.parse(RICH)));
        paragraph.setRichContent(RICH);
        paragraph.setContentOrigin("AI生成");
        paragraph.setContentSourceLevel("无来源");

        byte[] bytes = new DocxExporter().export(document, List.of(paragraph), null, true);

        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<XWPFParagraph> body = word.getParagraphs();
            assertThat(body).extracting(XWPFParagraph::getText).contains("一、总体要求必须落实。", "1. 加强组织领导", "2. 强化资金保障");
            List<XWPFRun> runs = body.get(1).getRuns();
            assertThat(runs).anyMatch(run -> "总体要求".equals(run.text()) && run.isBold());
            assertThat(runs)
                    .anyMatch(run -> "必须落实。".equals(run.text()) && run.getUnderline() == UnderlinePatterns.SINGLE);
            assertThat(word.getTables()).hasSize(1);
            assertThat(word.getTables().get(0).getRow(0).getCell(1).getText()).isEqualTo("金额");
            assertThat(word.getTables()
                            .get(0)
                            .getRow(0)
                            .getCell(0)
                            .getParagraphs()
                            .get(0)
                            .getRuns()
                            .get(0)
                            .isBold())
                    .isTrue();
            assertThat(word.getTables().get(0).getRow(1).getCell(0).getText()).isEqualTo("道路改造");
            assertThat(word.getComments()).hasSize(1);
        }
    }

    @Test
    void htmlExportEscapesTextAndRendersStructure() {
        String rich =
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"<b>注意</b>\",\"marks\":[{\"type\":\"italic\"}]}]}]}";
        assertThat(RichContent.toHtml(RichContent.parse(rich))).isEqualTo("<p><em>&lt;b&gt;注意&lt;/b&gt;</em></p>");
        assertThat(RichContent.toHtml(RichContent.parse(RICH)))
                .contains("<ol><li><p>加强组织领导</p></li>")
                .contains("<table><tr><th><p>项目</p></th>");
    }
}
