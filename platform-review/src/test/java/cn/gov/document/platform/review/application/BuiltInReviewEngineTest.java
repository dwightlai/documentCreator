package cn.gov.document.platform.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.rule.application.EffectiveReviewRuleProvider;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuiltInReviewEngineTest {
    private final EffectiveReviewRuleProvider provider = mock(EffectiveReviewRuleProvider.class);
    private final BuiltInReviewEngine engine = new BuiltInReviewEngine(provider);

    @Test
    void appliesConfiguredLevelsAndRiskThreshold() {
        DocumentParagraphEntity paragraph = paragraph("p1", 1, "截止目前为止，我们将做出贡献并核验帐号。");
        when(provider.inspect("通知", paragraph.getPlainText())).thenReturn(List.of());

        var l1General = engine.inspect("通知", List.of(paragraph), Set.of("L1"), "一般");
        assertThat(l1General)
                .extracting(BuiltInReviewEngine.Candidate::original)
                .containsExactly("截止目前为止");

        var l2 = engine.inspect("通知", List.of(paragraph), Set.of("L2"), "提示");
        assertThat(l2).extracting(BuiltInReviewEngine.Candidate::original).containsExactly("做出贡献");
    }

    @Test
    void detectsReportRequestAndTemplateResidueAsManualL3Issues() {
        DocumentParagraphEntity paragraph = paragraph("p2", 1, "有关情况报告如下，请批准实施{attachment_list}。");
        when(provider.inspect("报告", paragraph.getPlainText())).thenReturn(List.of());

        var issues = engine.inspect("报告", List.of(paragraph), Set.of("L3"), "一般");

        assertThat(issues)
                .extracting(BuiltInReviewEngine.Candidate::original)
                .containsExactly("请批准", "{attachment_list}");
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.level()).isEqualTo("L3");
            assertThat(issue.replacement()).isNull();
        });
    }

    @Test
    void detectsMultipleRequestMattersAndMissingEndingForSubstantiveRequest() {
        String body = "根据工作安排和项目建设实际，为进一步提升政务服务能力，现就有关建设事项请批准立项。"
                + "项目已经完成充分论证，资金测算和风险评估均具备实施条件，同时请同意安排专项资金，后续将严格履行采购程序并接受监督。"
                + "有关责任、实施计划和监督措施已经明确，具备立即组织实施的工作基础。";
        DocumentParagraphEntity paragraph = paragraph("p3", 1, body);
        when(provider.inspect("请示", body)).thenReturn(List.of());

        var issues = engine.inspect("请示", List.of(paragraph), Set.of("L3"), "一般");

        assertThat(issues).extracting(BuiltInReviewEngine.Candidate::basis).contains("请示应当一文一事", "党政机关公文请示结束语规则");
    }

    @Test
    void detectsTitleWithoutSelectedDocumentTypeAndKeepsLogicalParagraphNumber() {
        DocumentParagraphEntity paragraph = paragraph("p4", 9, "现将有关事项说明如下。");
        when(provider.inspect("通知", paragraph.getPlainText())).thenReturn(List.of());

        var issues = engine.inspect("通知", "关于开展专项工作的安排", List.of(paragraph), Set.of("L3"), "一般");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.original()).isEqualTo("关于开展专项工作的安排");
            assertThat(issue.paragraphNumber()).isEqualTo(1);
            assertThat(issue.basis()).contains("标题");
        });
    }

    private DocumentParagraphEntity paragraph(String id, int order, String text) {
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setId(id);
        paragraph.setParagraphOrder(order);
        paragraph.setPlainText(text);
        return paragraph;
    }
}
