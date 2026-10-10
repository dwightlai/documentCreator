package cn.gov.document.platform.common.file;

import cn.gov.document.platform.common.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Performs a bounded, parser-independent inspection before any uploaded DOCX is handed to POI.
 */
public final class DocxSecurityValidator {
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 5_000;
    private static final int MAX_RELATIONSHIP_BYTES = 2_000_000;
    private static final Pattern EXTERNAL_RELATIONSHIP =
            Pattern.compile("TargetMode\\s*=\\s*[\"']External[\"']", Pattern.CASE_INSENSITIVE);

    private DocxSecurityValidator() {}

    public static void validate(String fileName, byte[] bytes, long maxCompressedBytes, String label) {
        String safeLabel = label == null || label.isBlank() ? "DOCX 文件" : label;
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw BusinessException.unprocessable(safeLabel + "只允许 DOCX 文件");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > maxCompressedBytes) {
            throw BusinessException.unprocessable(safeLabel + "大小超出允许范围");
        }
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw BusinessException.unprocessable(safeLabel + "不是有效的 DOCX/ZIP 容器");
        }

        int entries = 0;
        long expanded = 0;
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw BusinessException.unprocessable(safeLabel + " ZIP 条目数量异常");
                }
                String originalName = entry.getName().replace('\\', '/');
                String name = originalName.toLowerCase(Locale.ROOT);
                if (originalName.startsWith("/")
                        || originalName.matches("^[a-zA-Z]:.*")
                        || originalName.contains("../")
                        || originalName.equals("..")) {
                    throw BusinessException.unprocessable(safeLabel + "包含不安全 ZIP 路径");
                }
                hasContentTypes |= "[content_types].xml".equals(name);
                hasDocument |= "word/document.xml".equals(name);
                if (name.contains("vbaproject.bin")
                        || name.startsWith("word/embeddings/")
                        || name.contains("oleobject")
                        || name.endsWith(".exe")
                        || name.endsWith(".dll")) {
                    throw BusinessException.unprocessable(safeLabel + "禁止宏、可执行文件、嵌入对象和 OLE 内容");
                }

                ByteArrayOutputStream relationship = name.endsWith(".rels") ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_UNCOMPRESSED_BYTES) {
                        throw BusinessException.unprocessable(safeLabel + "解压后体积异常");
                    }
                    if (relationship != null) {
                        if (relationship.size() + read > MAX_RELATIONSHIP_BYTES) {
                            throw BusinessException.unprocessable(safeLabel + "关系文件体积异常");
                        }
                        relationship.write(buffer, 0, read);
                    }
                }
                if (relationship != null
                        && EXTERNAL_RELATIONSHIP
                                .matcher(relationship.toString(StandardCharsets.UTF_8))
                                .find()) {
                    throw BusinessException.unprocessable(safeLabel + "禁止外部链接关系");
                }
            }
        } catch (IOException invalidZip) {
            throw BusinessException.unprocessable(safeLabel + " ZIP 容器损坏");
        }
        if (!hasContentTypes || !hasDocument) {
            throw BusinessException.unprocessable(safeLabel + "缺少 DOCX 必需结构");
        }
    }
}
