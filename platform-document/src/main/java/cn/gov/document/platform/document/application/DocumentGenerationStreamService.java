package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.DocumentDtos;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DocumentGenerationStreamService {
    private static final long TIMEOUT_MILLIS = 120_000;
    private final DocumentService documentService;
    private final TaskExecutor executor;

    public DocumentGenerationStreamService(
            DocumentService documentService, @Qualifier("generationStreamExecutor") TaskExecutor executor) {
        this.documentService = documentService;
        this.executor = executor;
    }

    public SseEmitter generate(String taskId, DocumentDtos.GenerateSectionRequest request) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        String requestId = RequestContext.requestId();
        String tenantId = RequestContext.tenantId();
        String userId = RequestContext.userId();
        try {
            executor.execute(() -> run(emitter, requestId, tenantId, userId, taskId, request));
        } catch (RejectedExecutionException exception) {
            throw new BusinessException("GENERATION_QUEUE_FULL", "流式生成队列已满，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
        return emitter;
    }

    private void run(
            SseEmitter emitter,
            String requestId,
            String tenantId,
            String userId,
            String taskId,
            DocumentDtos.GenerateSectionRequest request) {
        AtomicLong sequence = new AtomicLong();
        try {
            RequestContext.set(requestId, tenantId, userId);
            send(emitter, sequence, "start", Map.of("taskId", taskId, "message", "生成请求已进入执行队列"));
            DocumentDtos.DocumentResponse response = documentService.generateSectionStreaming(taskId, request);
            DocumentDtos.ParagraphResponse paragraph =
                    response.paragraphs().get(response.paragraphs().size() - 1);
            String output = paragraph.plainText();
            int offset = 0;
            while (offset < output.length()) {
                int codePoints = Math.min(24, output.codePointCount(offset, output.length()));
                int end = output.offsetByCodePoints(offset, codePoints);
                send(emitter, sequence, "delta", Map.of("text", output.substring(offset, end)));
                offset = end;
            }
            send(
                    emitter,
                    sequence,
                    "completed",
                    Map.of(
                            "documentId",
                            response.id(),
                            "paragraphId",
                            paragraph.id(),
                            "documentVersion",
                            response.currentVersion(),
                            "wordCount",
                            response.totalWordCount()));
            emitter.complete();
        } catch (Exception exception) {
            try {
                send(emitter, sequence, "error", Map.of("message", safeMessage(exception)));
            } catch (Exception ignored) {
            }
            emitter.complete();
        } finally {
            RequestContext.clear();
        }
    }

    private void send(SseEmitter emitter, AtomicLong sequence, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(sequence.incrementAndGet()))
                .name(event)
                .data(data));
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage() == null ? "流式生成失败" : exception.getMessage();
        return value.substring(0, Math.min(500, value.length()));
    }
}
