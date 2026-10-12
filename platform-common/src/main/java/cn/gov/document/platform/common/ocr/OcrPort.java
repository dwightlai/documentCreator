package cn.gov.document.platform.common.ocr;

import java.util.Optional;

/** 扫描件文字识别端口。未配置 OCR 服务时 {@link #enabled()} 为 false，调用方保持原有“请先完成 OCR”的提示。 */
public interface OcrPort {
    boolean enabled();

    /** 识别扫描版 PDF 或图片中的文字；服务未识别出文字时返回空。 */
    Optional<String> recognize(String filename, String contentType, byte[] content);
}
