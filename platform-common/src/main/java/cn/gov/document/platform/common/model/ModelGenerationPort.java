package cn.gov.document.platform.common.model;

import java.util.Map;
import java.util.Optional;

public interface ModelGenerationPort {
    Optional<Result> generate(
            String scene,
            String dataLevel,
            String documentTypeCode,
            String input,
            Map<String, Object> variables,
            String taskType,
            String taskId);

    record Result(
            String output,
            String modelConfigId,
            String modelVersion,
            String promptTemplateId,
            int promptTemplateVersion,
            int inputTokens,
            int outputTokens,
            int durationMs) {}
}
