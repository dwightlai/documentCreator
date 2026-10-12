package cn.gov.document.platform.document.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocxStructureInspectionTest {
    private final DocxFormatReviewService service = new DocxFormatReviewService(null);

    @Test
    void wellFormedUpwardDocumentPasses() throws Exception {
        byte[] docx = docx(
                "示政〔2026〕18号　　签发人：张某",
                "关于申请专项资金的请示",
                "省人民政府：",
                "正文内容。妥否，请批示。",
                "示范市人民政府",
                "2026年7月15日",
                "抄送：省财政厅。",
                "示范市人民政府办公室　2026年7月15日印发");
        assertThat(service.inspectStructure(docx, "上行")).isEmpty();
    }

    @Test
    void reportsMissingSignerWrongBracketChineseDateAndCopyBelowImprint() throws Exception {
        byte[] docx = docx("示政[2026]18号", "关于申请专项资金的请示", "正文内容。", "二〇二六年七月十五日", "示范市人民政府办公室　2026年7月15日印发", "抄送：省财政厅。");
        List<String> codes = service.inspectStructure(docx, "上行").stream()
                .map(DocxFormatReviewService.Deviation::dimensionCode)
                .toList();
        assertThat(codes)
                .containsExactlyInAnyOrder("SIGNER", "DOC_NUMBER_BRACKET", "DATE_NUMERALS", "COPY_TO_POSITION");
    }

    @Test
    void downwardDocumentDoesNotRequireSignerButNeedsImprint() throws Exception {
        byte[] docx = docx("示政发〔2026〕3号", "关于开展检查的通知", "各单位：", "正文。");
        assertThat(service.inspectStructure(docx, "下行"))
                .extracting(DocxFormatReviewService.Deviation::dimensionCode)
                .containsExactly("IMPRINT");
    }

    private static byte[] docx(String... lines) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String line : lines) document.createParagraph().createRun().setText(line);
            document.write(output);
            return output.toByteArray();
        }
    }
}
