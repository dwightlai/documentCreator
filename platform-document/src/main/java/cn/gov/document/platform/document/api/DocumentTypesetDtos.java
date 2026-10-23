package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.template.DocxFormatReviewService;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class DocumentTypesetDtos {
    private DocumentTypesetDtos() {}

    /**
     * @param templateId 指定输出模板；为空时依次使用写作任务的模板、文种默认模板，都没有时使用内置标准版式
     * @param values 版头、版记等要素：sender 发文机关标志、doc_number 发文字号、signer 签发人、recipient 主送机关、closing 结束语、
     *     attachment_desc 附件说明、date 成文日期、copy_to 抄送机关、issue_unit 印发机关、issue_date 印发日期
     */
    public record CreateRequest(@Size(max = 32) String templateId, Map<String, String> values) {}

    /** @param stale 正文已在排版后更新，需要重新套红 */
    public record TypesetResponse(
            String id,
            int documentVersion,
            boolean stale,
            String templateId,
            String templateName,
            Map<String, String> fieldValues,
            int deviationCount,
            List<DocxFormatReviewService.Deviation> deviations,
            String source,
            int fileSize,
            String createdBy,
            OffsetDateTime createdAt) {}
}
