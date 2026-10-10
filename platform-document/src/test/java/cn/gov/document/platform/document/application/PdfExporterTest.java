package cn.gov.document.platform.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

class PdfExporterTest {
    @Test
    void createsReadableMultipageChinesePdf() throws Exception {
        DocumentEntity document = new DocumentEntity();
        document.setTitle("关于推进政务服务标准化建设的请示");
        document.setCurrentVersion(6);
        String paragraph = "为深入贯彻落实有关工作要求，进一步提升政务服务规范化、标准化和便利化水平，现将有关事项请示如下。".repeat(24);
        DocumentParagraphEntity first = new DocumentParagraphEntity();
        first.setPlainText(paragraph);
        DocumentParagraphEntity second = new DocumentParagraphEntity();
        second.setPlainText("以上请示妥否，请批示。");
        byte[] bytes = new PdfExporter().export(document, List.of(first, second));
        Path output = Path.of("target", "test-output", "document-export-sample.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
        assertThat(bytes).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo(document.getTitle());
        }
    }
}
