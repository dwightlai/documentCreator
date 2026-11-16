package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.error.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OutputTemplateExchangeService {
    private static final String FORMAT = "gov-document-output-templates";
    private static final int MAX_FILE_CHARS = 28_000_000;
    private final OutputTemplateService templates;
    private final Validator validator;

    public OutputTemplateExchangeService(OutputTemplateService templates, Validator validator) {
        this.templates = templates;
        this.validator = validator;
    }

    public OutputTemplateExchangeDtos.Bundle exportTemplates() {
        List<OutputTemplateExchangeDtos.TemplateItem> items = templates.list(null, null).stream()
                .map(item -> new OutputTemplateExchangeDtos.TemplateItem(
                        item.templateCode(),
                        item.templateName(),
                        item.documentTypeCode(),
                        item.hasRedHeader(),
                        item.hasFooterMark(),
                        item.direction(),
                        item.description(),
                        item.templateCode() + ".docx",
                        Base64.getEncoder().encodeToString(templates.fileContent(item.id()))))
                .toList();
        return new OutputTemplateExchangeDtos.Bundle(FORMAT, 1, OffsetDateTime.now(), items);
    }

    @Transactional
    public OutputTemplateExchangeDtos.ImportResult importTemplates(OutputTemplateExchangeDtos.Bundle bundle) {
        if (bundle == null
                || !FORMAT.equals(bundle.format())
                || bundle.version() != 1
                || bundle.templates() == null
                || bundle.templates().isEmpty()
                || bundle.templates().size() > 50) throw BusinessException.unprocessable("输出模板文件格式无效，单次最多导入 50 个");
        Set<String> fileCodes = new HashSet<>();
        List<byte[]> files = new ArrayList<>();
        for (var item : bundle.templates()) {
            if (item == null || item.templateCode() == null) throw BusinessException.unprocessable("输出模板文件包含空记录");
            validate(
                    new OutputTemplateDtos.CreateMetadata(
                            item.templateCode(),
                            item.templateName(),
                            item.documentTypeCode(),
                            item.hasRedHeader(),
                            item.hasFooterMark(),
                            item.direction(),
                            item.description()),
                    "输出模板 " + item.templateCode());
            if (!fileCodes.add(item.templateCode().trim()))
                throw BusinessException.unprocessable("输出模板文件内代码重复：" + item.templateCode());
            files.add(decode(item));
        }
        Set<String> existing = new HashSet<>();
        for (var item : templates.list(null, null)) existing.add(item.templateCode());
        int imported = 0;
        List<String> skipped = new ArrayList<>();
        for (int i = 0; i < bundle.templates().size(); i++) {
            var item = bundle.templates().get(i);
            if (existing.contains(item.templateCode().trim())) {
                skipped.add(item.templateCode());
                continue;
            }
            try {
                templates.create(metadata(item), fileName(item), files.get(i));
            } catch (BusinessException e) {
                throw BusinessException.unprocessable("输出模板 " + item.templateCode() + " 导入失败：" + e.getMessage());
            }
            imported++;
        }
        return new OutputTemplateExchangeDtos.ImportResult(bundle.templates().size(), imported, skipped.size(), skipped);
    }

    private byte[] decode(OutputTemplateExchangeDtos.TemplateItem item) {
        if (!StringUtils.hasText(item.fileBase64()) || item.fileBase64().length() > MAX_FILE_CHARS)
            throw BusinessException.unprocessable("输出模板 " + item.templateCode() + " 的 DOCX 内容无效或超过 20MB");
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(item.fileBase64());
            if (bytes.length == 0 || bytes.length > 20 * 1024 * 1024)
                throw BusinessException.unprocessable("输出模板 " + item.templateCode() + " 的 DOCX 内容无效或超过 20MB");
            return bytes;
        } catch (IllegalArgumentException e) {
            throw BusinessException.unprocessable("输出模板 " + item.templateCode() + " 的 DOCX 内容不是有效的 Base64");
        }
    }

    private OutputTemplateDtos.CreateMetadata metadata(OutputTemplateExchangeDtos.TemplateItem item) {
        return new OutputTemplateDtos.CreateMetadata(
                item.templateCode(),
                item.templateName(),
                item.documentTypeCode(),
                item.hasRedHeader(),
                item.hasFooterMark(),
                item.direction(),
                item.description());
    }

    private String fileName(OutputTemplateExchangeDtos.TemplateItem item) {
        String name = item.fileName() == null ? "" : item.fileName().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) name = item.templateCode().trim() + ".docx";
        return name;
    }

    private <T> void validate(T bean, String label) {
        Set<ConstraintViolation<T>> violations = validator.validate(bean);
        if (!violations.isEmpty())
            throw BusinessException.unprocessable(label + "：" + violations.iterator().next().getMessage());
    }
}
