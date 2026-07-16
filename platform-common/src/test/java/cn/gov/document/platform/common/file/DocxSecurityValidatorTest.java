package cn.gov.document.platform.common.file;

import cn.gov.document.platform.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxSecurityValidatorTest {
    @Test
    void acceptsMinimalSafeContainer() throws Exception {
        assertThatCode(() -> DocxSecurityValidator.validate("safe.docx", docx(Map.of()), 1024 * 1024, "待审文稿"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExternalRelationshipsMacrosAndTraversal() throws Exception {
        assertRejected(Map.of("word/_rels/document.xml.rels",
                "<Relationship TargetMode=\"External\" Target=\"https://attacker.invalid/payload\"/>") );
        assertRejected(Map.of("word/vbaProject.bin", "macro"));
        assertRejected(Map.of("../outside.bin", "payload"));
        assertRejected(Map.of("word/embeddings/oleObject1.bin", "payload"));
    }

    @Test
    void rejectsZipThatOnlyPretendsToBeDocx() throws Exception {
        byte[] fake = zip(Map.of("readme.txt", "not a word document"));
        assertThatThrownBy(() -> DocxSecurityValidator.validate("fake.docx", fake, 1024 * 1024, "知识文档"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 DOCX 必需结构");
    }

    private void assertRejected(Map<String, String> extra) throws Exception {
        byte[] value = docx(extra);
        assertThatThrownBy(() -> DocxSecurityValidator.validate("unsafe.docx", value, 1024 * 1024, "待审文稿"))
                .isInstanceOf(BusinessException.class);
    }

    private byte[] docx(Map<String, String> extra) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<Types/>");
        entries.put("word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>");
        entries.putAll(extra);
        return zip(entries);
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
