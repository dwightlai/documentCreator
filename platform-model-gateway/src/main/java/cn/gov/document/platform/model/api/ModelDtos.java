package cn.gov.document.platform.model.api;

import cn.gov.document.platform.model.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public final class ModelDtos {
    private ModelDtos() {}

    public record ConfigRequest(
            @NotBlank @Size(max = 50) String configCode,
            @NotBlank @Size(max = 200) String configName,
            @NotBlank String provider,
            @NotBlank @Size(max = 100) String modelName,
            String modelVersion,
            @NotBlank String scene,
            @NotBlank String dataLevel,
            @Min(128) Integer maxContextTokens,
            @Min(1000) Integer timeoutMs,
            @Min(0) @Max(10) Integer maxRetries,
            @DecimalMin("0") BigDecimal costLimitDaily,
            boolean sensitiveWordFilter,
            boolean outputValidation,
            boolean active,
            @Size(max = 500) String endpointUrl,
            @Size(max = 100) String credentialRef,
            @DecimalMin("0") BigDecimal inputCostPer1k,
            @DecimalMin("0") BigDecimal outputCostPer1k,
            @Min(1) Integer qpsLimit,
            @Min(1) Integer dailyTokenLimit,
            Integer lockVersion) {}

    public record ConfigResponse(
            String id,
            String configCode,
            String configName,
            String provider,
            String modelName,
            String modelVersion,
            String scene,
            String dataLevel,
            Integer maxContextTokens,
            int timeoutMs,
            int maxRetries,
            BigDecimal costLimitDaily,
            boolean sensitiveWordFilter,
            boolean outputValidation,
            boolean active,
            String endpointUrl,
            String credentialRef,
            BigDecimal inputCostPer1k,
            BigDecimal outputCostPer1k,
            Integer qpsLimit,
            Integer dailyTokenLimit,
            int lockVersion,
            OffsetDateTime updatedAt) {
        public static ConfigResponse from(ModelConfigEntity e) {
            return new ConfigResponse(
                    e.getId(),
                    e.getConfigCode(),
                    e.getConfigName(),
                    e.getProvider(),
                    e.getModelName(),
                    e.getModelVersion(),
                    e.getScene(),
                    e.getDataLevel(),
                    e.getMaxContextTokens(),
                    e.getTimeoutMs(),
                    e.getMaxRetries(),
                    e.getCostLimitDaily(),
                    Boolean.TRUE.equals(e.getSensitiveWordFilter()),
                    Boolean.TRUE.equals(e.getOutputValidation()),
                    Boolean.TRUE.equals(e.getIsActive()),
                    e.getEndpointUrl(),
                    e.getCredentialRef(),
                    e.getInputCostPer1k(),
                    e.getOutputCostPer1k(),
                    e.getQpsLimit(),
                    e.getDailyTokenLimit(),
                    e.getLockVersion(),
                    e.getUpdatedAt());
        }
    }

    public record RoutePreviewRequest(
            @NotBlank String scene, @NotBlank String dataLevel, @NotBlank @Size(max = 30000) String input) {}

    public record RoutePreviewResponse(
            String modelConfigId,
            String configCode,
            String provider,
            String modelName,
            String dataLevel,
            String preparedInput,
            int redactedEntityCount,
            boolean cloudRoute) {}

    public record InvokeRequest(
            @NotBlank String scene,
            @NotBlank String dataLevel,
            @Size(max = 20) String documentTypeCode,
            @NotBlank @Size(max = 100000) String input,
            Map<String, Object> variables,
            @NotBlank @Size(max = 20) String taskType,
            @NotBlank @Size(max = 32) String taskId) {}

    public record InvokeResponse(
            String modelConfigId,
            String promptTemplateId,
            int promptTemplateVersion,
            String modelVersion,
            String output,
            int redactedEntityCount,
            int inputTokens,
            int outputTokens,
            BigDecimal costAmount,
            int durationMs) {}

    public record LogResponse(
            String id,
            String taskType,
            String taskId,
            String modelConfigId,
            String promptTemplateId,
            Integer promptTemplateVersion,
            String inputHash,
            String inputDataLevel,
            String inputSummary,
            String outputHash,
            Integer tokenCountInput,
            Integer tokenCountOutput,
            Integer durationMs,
            BigDecimal costAmount,
            Boolean success,
            String errorMessage,
            OffsetDateTime calledAt) {
        public static LogResponse from(ModelCallLogEntity e) {
            return new LogResponse(
                    e.getId(),
                    e.getTaskType(),
                    e.getTaskId(),
                    e.getModelConfigId(),
                    e.getPromptTemplateId(),
                    e.getPromptTemplateVersion(),
                    e.getInputHash(),
                    e.getInputDataLevel(),
                    e.getInputSummary(),
                    e.getOutputHash(),
                    e.getTokenCountInput(),
                    e.getTokenCountOutput(),
                    e.getDurationMs(),
                    e.getCostAmount(),
                    e.getSuccess(),
                    e.getErrorMessage(),
                    e.getCalledAt());
        }
    }
}
