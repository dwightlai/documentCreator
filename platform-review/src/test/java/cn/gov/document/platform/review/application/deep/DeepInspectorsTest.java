package cn.gov.document.platform.review.application.deep;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepInspectorsTest {
    private final FactDataInspector facts = new FactDataInspector();
    private final LogicInspector logic = new LogicInspector();
    private final ContentSafetyInspector safety = new ContentSafetyInspector();

    @Test
    void reportsBreakdownExceedingTotalWithCalculation() {
        var findings = facts.inspect(List.of(paragraph(1, "全市共建成服务站28个，其中城区15个、乡镇16个。")));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.risk()).isEqualTo("重要");
            assertThat(f.original()).isEqualTo("共建成服务站28个");
            assertThat(f.explanation()).contains("15 + 16 = 31个", "总数为 28个");
        });
    }

    @Test
    void ignoresMatchingBreakdownAndChangeAmountsInsideDetail() {
        var findings = facts.inspect(List.of(paragraph(1, "共完成改造30项，其中老旧小区18项、背街小巷12项，较上年增加5项。")));
        assertThat(findings).isEmpty();
    }

    @Test
    void recomputesGrowthRateAndIgnoresAccurateOnes() {
        var wrong = facts.inspect(List.of(paragraph(1, "办件量由200件增长到260件，增长35%。")));
        assertThat(wrong).singleElement().satisfies(f -> assertThat(f.explanation())
                .contains("= 30%", "正文为 35%"));
        assertThat(facts.inspect(List.of(paragraph(1, "办件量由200件增长到260件，增长30%。"))))
                .isEmpty();
    }

    @Test
    void flagsSharesAboveHundredPercentAndInvalidDates() {
        var findings = facts.inspect(List.of(paragraph(1, "三类事项分别占45%、35%、30%。会议定于2026年2月30日召开，期限为2026年5月1日至4月1日。")));
        assertThat(findings).extracting(Finding::original).contains("2026年2月30日", "2026年5月1日至4月1日");
        assertThat(findings).anySatisfy(f -> assertThat(f.explanation()).contains("110%"));
    }

    @Test
    void marksInconsistentMetricAcrossParagraphsForManualCheck() {
        var findings = facts.inspect(List.of(paragraph(1, "政务服务事项累计达到120项。"), paragraph(3, "目前政务服务事项累计达到126项。")));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.risk()).isEqualTo("提示");
            assertThat(f.explanation()).contains("待人工核实", "第 1 段", "120项", "126项");
        });
    }

    @Test
    void detectsDirectionContradictionAndBrokenNumbering() {
        var findings = logic.inspect(
                List.of(paragraph(1, "投诉量由80件下降到96件，下降20%。"), paragraph(2, "一、总体情况\n二、主要成效"), paragraph(3, "四、下一步安排")));
        assertThat(findings).anySatisfy(f -> assertThat(f.explanation()).contains("实际为增加", "下降"));
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.original()).isEqualTo("四、");
            assertThat(f.explanation()).contains("应为第 3 项");
        });
        // 方向矛盾只由 L6 报告，L5 不重复报告数值
        assertThat(facts.inspect(List.of(paragraph(1, "投诉量由80件下降到96件，下降20%。")))).isEmpty();
    }

    @Test
    void numberingResetsSubLevelsUnderNewParent() {
        var findings = logic.inspect(List.of(paragraph(1, "一、背景\n（一）现状\n（二）问题\n二、措施\n（一）完善制度")));
        assertThat(findings).isEmpty();
    }

    @Test
    void flagsPersonalInformationClassificationAndAbsoluteTerms() {
        var findings = safety.inspect(List.of(paragraph(1, "联系人身份证号11010519491231002X，手机13812345678。本项目零风险。秘密★1年")));
        assertThat(findings)
                .extracting(Finding::original)
                .containsExactlyInAnyOrder("11010519491231002X", "13812345678", "零风险", "秘密★");
        assertThat(findings)
                .filteredOn(f -> f.original().startsWith("1101"))
                .singleElement()
                .extracting(Finding::risk)
                .isEqualTo("严重");
    }

    @Test
    void ignoresEighteenDigitNumbersWithInvalidChecksum() {
        assertThat(safety.inspect(List.of(paragraph(1, "项目编号110105194912310021。"))))
                .isEmpty();
    }

    @Test
    void keepsOnlyModelFindingsWhoseQuoteExistsInTheParagraph() {
        ModelLogicReviewer reviewer = new ModelLogicReviewer(null, new ObjectMapper());
        var paragraphs = List.of(paragraph(1, "全年未发生安全事故。上半年发生两起安全事故。"));
        var findings = reviewer.parse(
                """
                说明文字
                [{"paragraph":1,"quote":"上半年发生两起安全事故","type":"前后矛盾","reason":"与全年未发生事故矛盾","suggestion":"核实事故数据"},
                 {"paragraph":1,"quote":"模型臆造的句子","type":"x","reason":"y","suggestion":"z"},
                 {"paragraph":9,"quote":"全年","type":"x","reason":"y","suggestion":"z"}]
                """,
                paragraphs);
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.level()).isEqualTo("L6");
            assertThat(f.start()).isEqualTo(10);
            assertThat(f.explanation()).contains("前后矛盾", "须人工确认");
        });
        assertThat(ModelLogicReviewer.dataLevel("涉密")).isNull();
    }

    private static DocumentParagraphEntity paragraph(int order, String text) {
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setId("p" + order);
        paragraph.setParagraphOrder(order);
        paragraph.setPlainText(text);
        return paragraph;
    }
}
