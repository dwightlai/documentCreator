package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.model.api.ModelDataExchangeDtos;
import cn.gov.document.platform.model.api.ModelDtos;
import cn.gov.document.platform.model.api.PromptTemplateDtos;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelDataExchangeService {
    private static final String MODEL_FORMAT = "gov-document-model-configs";
    private static final String PROMPT_FORMAT = "gov-document-prompt-templates";
    private final ModelGatewayService models;
    private final PromptTemplateService prompts;
    private final Validator validator;

    public ModelDataExchangeService(
            ModelGatewayService models, PromptTemplateService prompts, Validator validator) {
        this.models = models;
        this.prompts = prompts;
        this.validator = validator;
    }

    public ModelDataExchangeDtos.ModelBundle exportModels() {
        List<ModelDataExchangeDtos.ModelItem> items = models.configs(null).stream()
                .map(item -> new ModelDataExchangeDtos.ModelItem(
                        item.configCode(),
                        item.configName(),
                        item.provider(),
                        item.modelName(),
                        item.modelVersion(),
                        item.scene(),
                        item.dataLevel(),
                        item.maxContextTokens(),
                        item.timeoutMs(),
                        item.maxRetries(),
                        item.costLimitDaily(),
                        item.sensitiveWordFilter(),
                        item.outputValidation(),
                        item.active(),
                        item.endpointUrl(),
                        item.credentialRef(),
                        item.inputCostPer1k(),
                        item.outputCostPer1k(),
                        item.qpsLimit(),
                        item.dailyTokenLimit()))
                .toList();
        return new ModelDataExchangeDtos.ModelBundle(MODEL_FORMAT, 1, OffsetDateTime.now(), items);
    }

    @Transactional
    public ModelDataExchangeDtos.ImportResult importModels(ModelDataExchangeDtos.ModelBundle bundle) {
        if (bundle == null
                || !MODEL_FORMAT.equals(bundle.format())
                || bundle.version() != 1
                || bundle.models() == null
                || bundle.models().isEmpty()
                || bundle.models().size() > 200) throw BusinessException.unprocessable("模型路由文件格式无效，单次最多导入 200 条");
        Set<String> fileCodes = new HashSet<>();
        for (var item : bundle.models()) {
            if (item == null || item.configCode() == null) throw BusinessException.unprocessable("模型路由文件包含空记录");
            validate(modelRequest(item), "模型路由 " + item.configCode());
            if (!fileCodes.add(item.configCode().trim()))
                throw BusinessException.unprocessable("模型路由文件内代码重复：" + item.configCode());
        }
        Set<String> existing = new HashSet<>();
        for (var item : models.configs(null)) existing.add(item.configCode());
        int imported = 0;
        List<String> skipped = new ArrayList<>();
        for (var item : bundle.models()) {
            if (existing.contains(item.configCode().trim())) {
                skipped.add(item.configCode());
                continue;
            }
            try {
                models.create(modelRequest(item));
            } catch (BusinessException e) {
                throw BusinessException.unprocessable("模型路由 " + item.configCode() + " 导入失败：" + e.getMessage());
            }
            imported++;
        }
        return new ModelDataExchangeDtos.ImportResult(bundle.models().size(), imported, skipped.size(), skipped);
    }

    public ModelDataExchangeDtos.PromptBundle exportPrompts() {
        List<ModelDataExchangeDtos.PromptItem> items = prompts.list(null, null, null).stream()
                .map(item -> new ModelDataExchangeDtos.PromptItem(
                        item.templateCode(),
                        item.templateName(),
                        item.applicableScene(),
                        item.documentTypeCode(),
                        item.applicableModels(),
                        item.systemRole(),
                        item.documentTypeMethod(),
                        item.inputFields(),
                        item.materialCitationRule(),
                        item.antiFabricationRule(),
                        item.outputFormat(),
                        item.riskConstraint(),
                        item.modelParameters(),
                        item.testSampleIds()))
                .toList();
        return new ModelDataExchangeDtos.PromptBundle(PROMPT_FORMAT, 1, OffsetDateTime.now(), items);
    }

    @Transactional
    public ModelDataExchangeDtos.ImportResult importPrompts(ModelDataExchangeDtos.PromptBundle bundle) {
        if (bundle == null
                || !PROMPT_FORMAT.equals(bundle.format())
                || bundle.version() != 1
                || bundle.prompts() == null
                || bundle.prompts().isEmpty()
                || bundle.prompts().size() > 200) throw BusinessException.unprocessable("提示词模板文件格式无效，单次最多导入 200 条");
        Set<String> fileCodes = new HashSet<>();
        for (var item : bundle.prompts()) {
            if (item == null || item.templateCode() == null) throw BusinessException.unprocessable("提示词模板文件包含空记录");
            validate(promptRequest(item), "提示词模板 " + item.templateCode());
            if (!fileCodes.add(item.templateCode().trim()))
                throw BusinessException.unprocessable("提示词模板文件内代码重复：" + item.templateCode());
        }
        Set<String> existing = new HashSet<>();
        for (var item : prompts.list(null, null, null)) existing.add(item.templateCode());
        int imported = 0;
        List<String> skipped = new ArrayList<>();
        for (var item : bundle.prompts()) {
            if (existing.contains(item.templateCode().trim())) {
                skipped.add(item.templateCode());
                continue;
            }
            try {
                prompts.create(promptRequest(item));
            } catch (BusinessException e) {
                throw BusinessException.unprocessable("提示词模板 " + item.templateCode() + " 导入失败：" + e.getMessage());
            }
            imported++;
        }
        return new ModelDataExchangeDtos.ImportResult(bundle.prompts().size(), imported, skipped.size(), skipped);
    }

    private ModelDtos.ConfigRequest modelRequest(ModelDataExchangeDtos.ModelItem item) {
        return new ModelDtos.ConfigRequest(
                item.configCode(),
                item.configName(),
                item.provider(),
                item.modelName(),
                item.modelVersion(),
                item.scene(),
                item.dataLevel(),
                item.maxContextTokens(),
                item.timeoutMs(),
                item.maxRetries(),
                item.costLimitDaily(),
                item.sensitiveWordFilter(),
                item.outputValidation(),
                item.active(),
                item.endpointUrl(),
                item.credentialRef(),
                item.inputCostPer1k(),
                item.outputCostPer1k(),
                item.qpsLimit(),
                item.dailyTokenLimit(),
                null);
    }

    private PromptTemplateDtos.Request promptRequest(ModelDataExchangeDtos.PromptItem item) {
        return new PromptTemplateDtos.Request(
                item.templateCode(),
                item.templateName(),
                item.applicableScene(),
                item.documentTypeCode(),
                item.applicableModels(),
                item.systemRole(),
                item.documentTypeMethod(),
                item.inputFields(),
                item.materialCitationRule(),
                item.antiFabricationRule(),
                item.outputFormat(),
                item.riskConstraint(),
                item.modelParameters(),
                item.testSampleIds(),
                "从文件导入",
                null);
    }

    private <T> void validate(T bean, String label) {
        Set<ConstraintViolation<T>> violations = validator.validate(bean);
        if (!violations.isEmpty())
            throw BusinessException.unprocessable(label + "：" + violations.iterator().next().getMessage());
    }
}
