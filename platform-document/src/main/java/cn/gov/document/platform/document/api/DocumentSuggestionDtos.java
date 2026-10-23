package cn.gov.document.platform.document.api;

import cn.gov.document.platform.document.application.RichContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentSuggestionDtos {
    private DocumentSuggestionDtos() {}

    /** 修订模式下提交的段落修改，内容规则与直接保存段落相同。 */
    public record CreateRequest(
            @NotBlank String paragraphId,
            @NotNull Integer lockVersion,
            @NotBlank @Size(max = 30000) String plainText,
            @Size(max = RichContent.MAX_LENGTH) String richContent,
            @Size(max = 20) List<String> adoptedGenerationIds) {}

    public record DecisionRequest(@Size(max = 500) String note) {}

    /**
     * @param currentParagraphId 当前版本中同一位置的段落，已删除时为空
     * @param stale 待处理的修订其原文已被修改，不能再接受
     * @param aiAssisted 修订中采用了 AI 划词改写候选，接受后段落标记为 AI 改写
     */
    public record SuggestionResponse(
            String id,
            String documentId,
            String baseParagraphId,
            int baseVersion,
            String currentParagraphId,
            boolean stale,
            String status,
            String proposerId,
            String basePlainText,
            String proposedPlainText,
            String proposedRichContent,
            boolean aiAssisted,
            String decidedBy,
            OffsetDateTime decidedAt,
            String decisionNote,
            Integer acceptedVersion,
            OffsetDateTime createdAt) {}
}
