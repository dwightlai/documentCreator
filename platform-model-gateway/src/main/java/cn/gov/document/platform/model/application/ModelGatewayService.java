package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.coordination.CoordinationStore;
import cn.gov.document.platform.common.coordination.ExclusiveGuard;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.model.ModelGenerationPort;
import cn.gov.document.platform.model.api.ModelDtos;
import cn.gov.document.platform.model.domain.*;
import cn.gov.document.platform.model.persistence.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ModelGatewayService implements ModelGenerationPort {
    private static final List<String> LEVELS = List.of("普通", "内部", "敏感", "涉密");
    private final ModelConfigMapper configMapper;
    private final ModelCallLogMapper logMapper;
    private final ModelEndpointSecurity endpointSecurity;
    private final PromptTemplateService promptService;
    private final OpenAiCompatibleModelClient providerClient;
    private final CoordinationStore coordination;
    private final ModelCircuitBreaker circuitBreaker;

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));

    public ModelGatewayService(
            ModelConfigMapper configMapper,
            ModelCallLogMapper logMapper,
            ModelEndpointSecurity endpointSecurity,
            PromptTemplateService promptService,
            OpenAiCompatibleModelClient providerClient,
            CoordinationStore coordination,
            ModelCircuitBreaker circuitBreaker) {
        this.configMapper = configMapper;
        this.logMapper = logMapper;
        this.endpointSecurity = endpointSecurity;
        this.promptService = promptService;
        this.providerClient = providerClient;
        this.coordination = coordination;
        this.circuitBreaker = circuitBreaker;
    }

    public List<ModelDtos.ConfigResponse> configs(String scene) {
        return configMapper
                .selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(StringUtils.hasText(scene), ModelConfigEntity::getScene, scene)
                        .orderByAsc(ModelConfigEntity::getScene)
                        .orderByAsc(ModelConfigEntity::getConfigCode))
                .stream()
                .map(ModelDtos.ConfigResponse::from)
                .toList();
    }

    @Transactional
    public ModelDtos.ConfigResponse create(ModelDtos.ConfigRequest r) {
        validate(r);
        if (configMapper.selectCount(new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(ModelConfigEntity::getConfigCode, r.configCode().trim()))
                > 0) throw BusinessException.conflict("模型配置代码已存在");
        ModelConfigEntity e = new ModelConfigEntity();
        e.setTenantId(RequestContext.tenantId());
        apply(e, r);
        configMapper.insert(e);
        return ModelDtos.ConfigResponse.from(e);
    }

    @Transactional
    public ModelDtos.ConfigResponse update(String id, ModelDtos.ConfigRequest r) {
        validate(r);
        ModelConfigEntity e = require(id);
        if (r.lockVersion() == null) throw BusinessException.unprocessable("更新模型配置必须提交 lockVersion");
        ModelConfigEntity duplicate = configMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getConfigCode, r.configCode().trim()));
        if (duplicate != null && !duplicate.getId().equals(id)) throw BusinessException.conflict("模型配置代码已存在");
        e.setLockVersion(r.lockVersion());
        apply(e, r);
        if (configMapper.updateById(e) != 1) throw BusinessException.conflict("模型配置已被其他用户修改");
        return ModelDtos.ConfigResponse.from(configMapper.selectById(id));
    }

    public ModelDtos.RoutePreviewResponse prepare(ModelDtos.RoutePreviewRequest request) {
        Route route = route(request.scene(), request.dataLevel(), request.input());
        return new ModelDtos.RoutePreviewResponse(
                route.config().getId(),
                route.config().getConfigCode(),
                route.config().getProvider(),
                route.config().getModelName(),
                route.config().getDataLevel(),
                route.redaction().text(),
                route.redaction().count(),
                !isPrivate(route.config()));
    }

    /**
     * 按路由顺序依次尝试候选模型：熔断中的模型直接跳过；端点、上下文、配额、限流等配置性拒绝改试下一个；
     * 调用失败计入熔断统计后改用备用模型（§27）。调用日志为追加型审计记录，失败时不回滚。
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public ModelDtos.InvokeResponse invoke(ModelDtos.InvokeRequest request) {
        long started = System.nanoTime();
        List<ModelConfigEntity> candidates = candidates(request.scene(), request.dataLevel());
        Map<String, Object> variables =
                new LinkedHashMap<>(request.variables() == null ? Map.of() : request.variables());
        variables.put("input", PromptInjectionDefense.INPUT_PLACEHOLDER);
        BusinessException firstRejection = null;
        RuntimeException lastFailure = null;
        boolean anyClosed = false;
        for (ModelConfigEntity config : candidates) {
            if (circuitBreaker.isOpen(config.getId())) continue;
            anyClosed = true;
            Redaction redaction = redactionFor(config, request.input());
            int estimatedInput = estimate(redaction.text());
            PromptTemplateService.ResolvedPrompt prompt;
            try {
                if (!StringUtils.hasText(config.getEndpointUrl()))
                    throw BusinessException.unprocessable("所选模型尚未配置实际调用端点");
                endpointSecurity.validate(config.getEndpointUrl());
                if (config.getMaxContextTokens() != null && estimatedInput > config.getMaxContextTokens())
                    throw BusinessException.unprocessable("输入超过模型最大上下文");
                enforceDailyQuota(config, estimatedInput);
                prompt = promptService
                        .resolve(request.scene(), request.documentTypeCode(), config.getModelName(), variables)
                        .orElseThrow(() -> BusinessException.unprocessable("当前场景、文种和模型没有已生效提示词模板"));
                enforceRate(config);
            } catch (BusinessException rejection) {
                if (firstRejection == null) firstRejection = rejection;
                continue;
            }
            OpenAiCompatibleModelClient.Result result = null;
            RuntimeException failure = null;
            int attempts = Math.max(1, config.getMaxRetries() + 1);
            for (int i = 0; i < attempts; i++) {
                try {
                    result = providerClient.invoke(config, prompt.prompt(), redaction.text(), prompt.modelParameters());
                    failure = null;
                    break;
                } catch (RuntimeException e) {
                    failure = e;
                }
            }
            boolean emptyOutput = failure == null
                    && Boolean.TRUE.equals(config.getOutputValidation())
                    && !StringUtils.hasText(result.output());
            if (emptyOutput) failure = BusinessException.unprocessable("模型输出校验失败");
            int duration = elapsedMillis(started);
            if (failure != null) {
                circuitBreaker.recordFailure(config.getId());
                appendLog(
                        request.taskType(),
                        request.taskId(),
                        config,
                        prompt,
                        request.dataLevel(),
                        request.input(),
                        null,
                        emptyOutput ? result.inputTokens() : estimatedInput,
                        emptyOutput ? result.outputTokens() : 0,
                        BigDecimal.ZERO,
                        duration,
                        false,
                        emptyOutput ? "模型输出为空" : failure.getMessage());
                lastFailure = failure;
                continue;
            }
            circuitBreaker.recordSuccess(config.getId());
            int in = result.inputTokens() > 0 ? result.inputTokens() : estimatedInput;
            int out = result.outputTokens() > 0 ? result.outputTokens() : estimate(result.output());
            BigDecimal cost = cost(config, in, out);
            appendLog(
                    request.taskType(),
                    request.taskId(),
                    config,
                    prompt,
                    request.dataLevel(),
                    request.input(),
                    result.output(),
                    in,
                    out,
                    cost,
                    duration,
                    true,
                    null);
            return new ModelDtos.InvokeResponse(
                    config.getId(),
                    prompt.templateId(),
                    prompt.version(),
                    config.getModelVersion(),
                    redaction.restore(result.output()),
                    redaction.count(),
                    in,
                    out,
                    cost,
                    duration);
        }
        if (lastFailure != null) throw lastFailure;
        if (firstRejection != null) throw firstRejection;
        if (!anyClosed) throw BusinessException.unprocessable("当前场景可用模型均处于熔断保护中，请稍后重试");
        throw BusinessException.unprocessable("当前场景没有允许处理该数据级别的可用模型");
    }

    @Override
    @Transactional(noRollbackFor = RuntimeException.class)
    public Optional<ModelGenerationPort.Result> generate(
            String scene,
            String dataLevel,
            String documentTypeCode,
            String input,
            Map<String, Object> variables,
            String taskType,
            String taskId) {
        long available = configMapper.selectCount(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getScene, scene)
                .eq(ModelConfigEntity::getIsActive, true)
                .isNotNull(ModelConfigEntity::getEndpointUrl));
        if (available == 0) return Optional.empty();
        ModelDtos.InvokeResponse response = invoke(
                new ModelDtos.InvokeRequest(scene, dataLevel, documentTypeCode, input, variables, taskType, taskId));
        return Optional.of(new ModelGenerationPort.Result(
                response.output(),
                response.modelConfigId(),
                response.modelVersion(),
                response.promptTemplateId(),
                response.promptTemplateVersion(),
                response.inputTokens(),
                response.outputTokens(),
                response.durationMs()));
    }

    public List<ModelDtos.LogResponse> logs(String taskType, String taskId) {
        return logMapper
                .selectList(new LambdaQueryWrapper<ModelCallLogEntity>()
                        .eq(StringUtils.hasText(taskType), ModelCallLogEntity::getTaskType, taskType)
                        .eq(StringUtils.hasText(taskId), ModelCallLogEntity::getTaskId, taskId)
                        .orderByDesc(ModelCallLogEntity::getCalledAt)
                        .last("LIMIT 500"))
                .stream()
                .map(ModelDtos.LogResponse::from)
                .toList();
    }

    private Route route(String scene, String dataLevel, String input) {
        ModelConfigEntity selected = candidates(scene, dataLevel).stream()
                .filter(c -> !circuitBreaker.isOpen(c.getId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.unprocessable("当前场景可用模型均处于熔断保护中，请稍后重试"));
        return new Route(selected, redactionFor(selected, input));
    }

    /** 允许处理该数据级别的模型，按私有优先、配置代码排序；首个为主模型，其余为备用模型。 */
    private List<ModelConfigEntity> candidates(String scene, String dataLevel) {
        if ("涉密".equals(dataLevel)) throw BusinessException.unprocessable("涉密数据禁止通过模型网关处理");
        if (!LEVELS.contains(dataLevel)) throw BusinessException.unprocessable("无效数据级别");
        List<ModelConfigEntity> candidates = configMapper
                .selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(ModelConfigEntity::getScene, scene)
                        .eq(ModelConfigEntity::getIsActive, true))
                .stream()
                .filter(c -> rank(c.getDataLevel()) >= rank(dataLevel))
                .filter(c -> !"敏感".equals(dataLevel) || isPrivate(c))
                .sorted(Comparator.comparing((ModelConfigEntity c) -> isPrivate(c) ? 0 : 1)
                        .thenComparing(ModelConfigEntity::getConfigCode))
                .toList();
        if (candidates.isEmpty()) throw BusinessException.unprocessable("当前场景没有允许处理该数据级别的可用模型");
        return candidates;
    }

    private Redaction redactionFor(ModelConfigEntity config, String input) {
        return !isPrivate(config) || Boolean.TRUE.equals(config.getSensitiveWordFilter())
                ? redact(input)
                : new Redaction(input, Map.of());
    }

    private static int elapsedMillis(long startedNanos) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000L));
    }

    /** QPS 计数保存在协调存储中，多实例部署时全局生效。 */
    private void enforceRate(ModelConfigEntity config) {
        if (config.getQpsLimit() == null) return;
        String key = ExclusiveGuard.tenantKey("model:" + config.getId() + ":qps");
        if (!coordination.tryAcquirePermit(key, config.getQpsLimit(), Duration.ofSeconds(1)))
            throw BusinessException.unprocessable("模型调用超过 QPS 限制");
    }

    private void enforceDailyQuota(ModelConfigEntity config, int nextTokens) {
        if (config.getDailyTokenLimit() == null && config.getCostLimitDaily() == null) return;
        OffsetDateTime start =
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        List<ModelCallLogEntity> logs = logMapper.selectList(new LambdaQueryWrapper<ModelCallLogEntity>()
                .eq(ModelCallLogEntity::getModelConfigId, config.getId())
                .ge(ModelCallLogEntity::getCalledAt, start)
                .eq(ModelCallLogEntity::getSuccess, true));
        long tokens = logs.stream()
                .mapToLong(v -> zero(v.getTokenCountInput()) + zero(v.getTokenCountOutput()))
                .sum();
        BigDecimal cost = logs.stream()
                .map(v -> v.getCostAmount() == null ? BigDecimal.ZERO : v.getCostAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (config.getDailyTokenLimit() != null && tokens + nextTokens > config.getDailyTokenLimit())
            throw BusinessException.unprocessable("模型日 Token 配额不足");
        if (config.getCostLimitDaily() != null && cost.compareTo(config.getCostLimitDaily()) >= 0)
            throw BusinessException.unprocessable("模型日费用额度已用尽");
    }

    private void appendLog(
            String taskType,
            String taskId,
            ModelConfigEntity config,
            PromptTemplateService.ResolvedPrompt prompt,
            String dataLevel,
            String input,
            String output,
            int inputTokens,
            int outputTokens,
            BigDecimal cost,
            int duration,
            boolean success,
            String error) {
        ModelCallLogEntity e = new ModelCallLogEntity();
        e.setTenantId(RequestContext.tenantId());
        e.setTaskType(taskType);
        e.setTaskId(taskId);
        e.setModelConfigId(config.getId());
        e.setPromptTemplateId(prompt.templateId());
        e.setPromptTemplateVersion(prompt.version());
        e.setInputHash(sha256(input));
        e.setInputDataLevel(dataLevel);
        e.setInputSummary("chars=" + input.codePointCount(0, input.length()) + "; raw_content_logged=false");
        e.setOutputHash(output == null ? null : sha256(output));
        e.setTokenCountInput(inputTokens);
        e.setTokenCountOutput(outputTokens);
        e.setDurationMs(duration);
        e.setCostAmount(cost);
        e.setSuccess(success);
        e.setErrorMessage(error == null ? null : error.substring(0, Math.min(2000, error.length())));
        e.setCalledAt(OffsetDateTime.now());
        logMapper.insert(e);
    }

    private void validate(ModelDtos.ConfigRequest r) {
        if (!LEVELS.contains(r.dataLevel())) throw BusinessException.unprocessable("无效数据级别");
        if (r.provider().contains("云") && "涉密".equals(r.dataLevel()))
            throw BusinessException.unprocessable("云端模型不得配置为处理涉密数据");
        if (StringUtils.hasText(r.endpointUrl())) endpointSecurity.validate(r.endpointUrl());
        if (StringUtils.hasText(r.credentialRef()) && !r.credentialRef().matches("[A-Z][A-Z0-9_]{2,99}"))
            throw BusinessException.unprocessable("模型密钥引用名称不合法");
    }

    private void apply(ModelConfigEntity e, ModelDtos.ConfigRequest r) {
        e.setConfigCode(r.configCode().trim());
        e.setConfigName(r.configName().trim());
        e.setProvider(r.provider());
        e.setModelName(r.modelName());
        e.setModelVersion(r.modelVersion());
        e.setScene(r.scene());
        e.setDataLevel(r.dataLevel());
        e.setMaxContextTokens(r.maxContextTokens());
        e.setTimeoutMs(r.timeoutMs() == null ? 60000 : r.timeoutMs());
        e.setMaxRetries(r.maxRetries() == null ? 3 : r.maxRetries());
        e.setCostLimitDaily(r.costLimitDaily());
        e.setSensitiveWordFilter(r.sensitiveWordFilter());
        e.setOutputValidation(r.outputValidation());
        e.setIsActive(r.active());
        e.setEndpointUrl(r.endpointUrl());
        e.setCredentialRef(r.credentialRef());
        e.setInputCostPer1k(r.inputCostPer1k());
        e.setOutputCostPer1k(r.outputCostPer1k());
        e.setQpsLimit(r.qpsLimit());
        e.setDailyTokenLimit(r.dailyTokenLimit());
    }

    private ModelConfigEntity require(String id) {
        ModelConfigEntity e = configMapper.selectById(id);
        if (e == null) throw BusinessException.notFound("模型配置不存在");
        return e;
    }

    private boolean isPrivate(ModelConfigEntity e) {
        return e.getProvider().contains("本地") || e.getProvider().contains("私有");
    }

    private int rank(String level) {
        return LEVELS.indexOf(level);
    }

    /**
     * 将手机号、身份证号、邮箱替换为编号占位符并保留映射；模型输出中的占位符在返回前反替换为原值（§32.5）。
     * 映射只存在于本次调用的内存中，不写入日志或数据库。
     */
    private Redaction redact(String input) {
        Map<String, String> mapping = new LinkedHashMap<>();
        Map<String, String> assigned = new HashMap<>();
        String value = input;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(value);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String placeholder = assigned.computeIfAbsent(matcher.group(), original -> {
                    String token = "[已脱敏-" + (mapping.size() + 1) + "]";
                    mapping.put(token, original);
                    return token;
                });
                matcher.appendReplacement(result, Matcher.quoteReplacement(placeholder));
            }
            matcher.appendTail(result);
            value = result.toString();
        }
        return new Redaction(value, mapping);
    }

    private BigDecimal cost(ModelConfigEntity c, int in, int out) {
        BigDecimal input = c.getInputCostPer1k() == null
                ? BigDecimal.ZERO
                : c.getInputCostPer1k().multiply(BigDecimal.valueOf(in));
        BigDecimal output = c.getOutputCostPer1k() == null
                ? BigDecimal.ZERO
                : c.getOutputCostPer1k().multiply(BigDecimal.valueOf(out));
        return input.add(output).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private int estimate(String v) {
        return Math.max(1, (int) Math.ceil(v.codePointCount(0, v.length()) / 1.8));
    }

    private int zero(Integer v) {
        return v == null ? 0 : v;
    }

    private record Redaction(String text, Map<String, String> mapping) {
        int count() {
            return mapping.size();
        }

        String restore(String output) {
            if (output == null || mapping.isEmpty()) return output;
            String restored = output;
            for (Map.Entry<String, String> entry : mapping.entrySet())
                restored = restored.replace(entry.getKey(), entry.getValue());
            return restored;
        }
    }

    private record Route(ModelConfigEntity config, Redaction redaction) {}
}
