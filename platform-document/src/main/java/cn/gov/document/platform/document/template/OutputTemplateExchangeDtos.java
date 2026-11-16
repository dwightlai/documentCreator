package cn.gov.document.platform.document.template;

import java.time.OffsetDateTime;
import java.util.List;

public final class OutputTemplateExchangeDtos {
    private OutputTemplateExchangeDtos() {}

    public record TemplateItem(
            String templateCode,
            String templateName,
            String documentTypeCode,
            boolean hasRedHeader,
            boolean hasFooterMark,
            String direction,
            String description,
            String fileName,
            String fileBase64) {}

    public record Bundle(String format, int version, OffsetDateTime exportedAt, List<TemplateItem> templates) {}

    public record ImportResult(int total, int imported, int skipped, List<String> skippedCodes) {}
}
