package cn.gov.document.platform.model.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ModelDataExchangeDtos {
    private ModelDataExchangeDtos() {}

    public record ModelItem(
            String configCode,
            String configName,
            String provider,
            String modelName,
            String modelVersion,
            String scene,
            String dataLevel,
            Integer maxContextTokens,
            Integer timeoutMs,
            Integer maxRetries,
            BigDecimal costLimitDaily,
            boolean sensitiveWordFilter,
            boolean outputValidation,
            boolean active,
            String endpointUrl,
            String credentialRef,
            BigDecimal inputCostPer1k,
            BigDecimal outputCostPer1k,
            Integer qpsLimit,
            Integer dailyTokenLimit) {}

    public record ModelBundle(String format, int version, OffsetDateTime exportedAt, List<ModelItem> models) {}

    public record PromptItem(
            String templateCode,
            String templateName,
            String applicableScene,
            String documentTypeCode,
            List<String> applicableModels,
            String systemRole,
            String documentTypeMethod,
            Map<String, Object> inputFields,
            String materialCitationRule,
            String antiFabricationRule,
            String outputFormat,
            String riskConstraint,
            Map<String, Object> modelParameters,
            List<String> testSampleIds) {}

    public record PromptBundle(String format, int version, OffsetDateTime exportedAt, List<PromptItem> prompts) {}

    public record ImportResult(int total, int imported, int skipped, List<String> skippedCodes) {}
}
