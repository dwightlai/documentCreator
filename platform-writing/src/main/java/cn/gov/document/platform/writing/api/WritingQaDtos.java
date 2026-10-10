package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.writing.domain.WritingQaEntity;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class WritingQaDtos {
    private WritingQaDtos() {}

    public record QuestionRequest(@NotBlank @Size(max = 2000) String question) {}

    public record InsertRequest(String afterParagraphId, boolean confirmed) {}

    public record SourceReference(
            String sourceId,
            String sourceName,
            String documentId,
            String documentTitle,
            String chunkId,
            String snippet,
            String authorityLevel,
            String fileHash,
            double score) {}

    public record Response(
            String id,
            String taskId,
            String question,
            String answer,
            List<SourceReference> sources,
            String answerType,
            boolean inserted,
            String insertedDocumentId,
            String insertedParagraphId,
            int lockVersion,
            OffsetDateTime createdAt) {
        public static Response from(WritingQaEntity e, List<SourceReference> sources) {
            return new Response(
                    e.getId(),
                    e.getTaskId(),
                    e.getQuestionText(),
                    e.getAnswerText(),
                    sources,
                    e.getAnswerType(),
                    Boolean.TRUE.equals(e.getInserted()),
                    e.getInsertedDocumentId(),
                    e.getInsertedParagraphId(),
                    e.getLockVersion(),
                    e.getCreatedAt());
        }
    }
}
