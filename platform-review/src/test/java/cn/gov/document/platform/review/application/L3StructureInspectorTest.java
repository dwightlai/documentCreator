package cn.gov.document.platform.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.review.application.deep.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class L3StructureInspectorTest {
    private final L3StructureInspector inspector = new L3StructureInspector();

    @Test
    void requestMustHaveSingleAddresseeAndMustNotCopySubordinates() {
        var findings = inspector.inspect(
                "请示",
                "上行",
                List.of(
                        paragraph(1, "省人民政府、省财政厅："),
                        paragraph(2, "为推进项目建设，恳请予以支持。妥否，请批示。"),
                        paragraph(3, "抄送：各县（区）人民政府")));
        assertThat(findings).extracting(Finding::original).contains("省人民政府、省财政厅", "各县（区）人民政府");
        assertThat(findings)
                .filteredOn(f -> f.original().equals("各县（区）人民政府"))
                .extracting(Finding::explanation)
                .anySatisfy(text -> assertThat(text).contains("不得同时抄送下级机关"))
                .anySatisfy(text -> assertThat(text).contains("句号"));
    }

    @Test
    void upwardDocumentMustNotBeAddressedToLeaderOrUseOrders() {
        var findings = inspector.inspect("报告", "上行", List.of(paragraph(1, "张市长：\n现将有关情况报告如下，责成各部门立即执行。")));
        assertThat(findings).extracting(Finding::original).contains("张市长", "责成", "立即执行");
    }

    @Test
    void downwardDocumentMustNotPlead() {
        var findings = inspector.inspect("通知", "下行", List.of(paragraph(1, "各单位：\n请按要求落实。妥否，请批示。")));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.original()).isEqualTo("妥否，请批示");
            assertThat(f.risk()).isEqualTo("重要");
            assertThat(f.start()).isEqualTo("各单位：\n请按要求落实。".length());
        });
    }

    @Test
    void attachmentReferencesMustMatchAttachmentList() {
        var findings = inspector.inspect(
                "通知", "下行", List.of(paragraph(1, "具体安排见附件1，评分标准见附件3。"), paragraph(2, "附件：1. 工作安排\n2. 报名表")));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.original()).isEqualTo("附件3");
            assertThat(f.explanation()).contains("只列出 2 项");
        });
        assertThat(inspector.inspect("通知", "下行", List.of(paragraph(1, "详见附件。"))))
                .singleElement()
                .extracting(Finding::explanation)
                .asString()
                .contains("未见“附件：”说明");
    }

    @Test
    void cleanDocumentHasNoFindings() {
        var findings = inspector.inspect(
                "函", "平行", List.of(paragraph(1, "市交通运输局：\n为做好联合检查工作，商请贵局派员参加。盼复。"), paragraph(2, "抄送：市应急管理局。")));
        assertThat(findings).isEmpty();
    }

    private static DocumentParagraphEntity paragraph(int order, String text) {
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setId("p" + order);
        paragraph.setParagraphOrder(order);
        paragraph.setPlainText(text);
        return paragraph;
    }
}
