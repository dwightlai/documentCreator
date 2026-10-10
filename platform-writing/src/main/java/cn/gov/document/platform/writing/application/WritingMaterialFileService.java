package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import cn.gov.document.platform.common.storage.ObjectStorage;
import cn.gov.document.platform.writing.api.WritingMaterialDtos;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WritingMaterialFileService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final WritingMaterialService materialService;
    private final ObjectStorage objectStorage;

    public WritingMaterialFileService(WritingMaterialService materialService, ObjectStorage objectStorage) {
        this.materialService = materialService;
        this.objectStorage = objectStorage;
    }

    public WritingMaterialDtos.Response upload(
            String taskId,
            MultipartFile file,
            String authorityLevel,
            String usageMode,
            String citationPurpose,
            String permissionStatus) {
        if (file == null || file.isEmpty()) throw BusinessException.unprocessable("请选择材料文件");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.unprocessable("材料文件不得超过 20MB");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw BusinessException.unprocessable("材料文件读取失败");
        }
        String originalName =
                safeFilename(Optional.ofNullable(file.getOriginalFilename()).orElse("material.txt"));
        String extracted = extract(originalName, bytes);
        if (!StringUtils.hasText(extracted)) throw BusinessException.unprocessable("文件未提取到文本，扫描版 PDF 请先完成 OCR");
        String hash = sha256(bytes);
        String key = RequestContext.tenantId() + "/writing-materials/" + taskId + "/"
                + UUID.randomUUID().toString().replace("-", "") + "/" + originalName;
        objectStorage.put(key, bytes, Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"));
        return materialService.create(
                taskId,
                new WritingMaterialDtos.CreateRequest(
                        originalName,
                        "上传",
                        null,
                        defaultValue(authorityLevel, "内部资料"),
                        defaultValue(usageMode, "全文参考"),
                        defaultValue(citationPurpose, "事实依据"),
                        null,
                        null,
                        defaultValue(permissionStatus, "允许"),
                        extracted,
                        false,
                        hash,
                        key));
    }

    private String extract(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".txt")) return new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
            if (lower.endsWith(".docx")) {
                DocxSecurityValidator.validate(filename, bytes, MAX_FILE_SIZE, "写作材料");
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    String paragraphs = document.getParagraphs().stream()
                            .map(value -> value.getText())
                            .collect(Collectors.joining("\n"));
                    String tables = document.getTables().stream()
                            .flatMap(table -> table.getRows().stream())
                            .flatMap(row -> row.getTableCells().stream())
                            .map(cell -> cell.getText())
                            .collect(Collectors.joining("\n"));
                    return paragraphs + (tables.isBlank() ? "" : "\n" + tables);
                }
            }
            if (lower.endsWith(".pdf")) {
                try (var document = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(document);
                }
            }
            throw BusinessException.unprocessable("写作材料仅支持 DOCX、PDF 和 TXT");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) throw businessException;
            throw BusinessException.unprocessable("材料文件已损坏或无法解析");
        }
    }

    private String safeFilename(String value) {
        String filename = value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (filename.isBlank() || filename.length() > 180) throw BusinessException.unprocessable("材料文件名不合法或过长");
        return filename;
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
