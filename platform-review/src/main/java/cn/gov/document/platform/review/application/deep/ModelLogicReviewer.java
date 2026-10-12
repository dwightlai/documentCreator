package cn.gov.document.platform.review.application.deep;

import cn.gov.document.platform.common.model.ModelGenerationPort;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型辅助逻辑审校（§23）：“大模型提出问题 + 规则验证 + 人工确认”。
 *
 * <p>模型须返回 JSON 数组，每项包含段落编号、原文引用、问题类型、判断理由和建议修改方向。引用必须能在对应段落中逐字找到，
 * 否则视为模型臆造而丢弃。未配置“逻辑审校”场景的生效模型、数据为涉密或模型调用失败时，静默跳过，不影响规则审校结果。
 */
@Component
public class ModelLogicReviewer {
    public static final String SCENE = "逻辑审校";
    private static final Logger log = LoggerFactory.getLogger(ModelLogicReviewer.class);
    private static final int MAX_FINDINGS = 20;

    private final ObjectProvider<ModelGenerationPort> modelPort;
    private final ObjectMapper objectMapper;

    public ModelLogicReviewer(ObjectProvider<ModelGenerationPort> modelPort, ObjectMapper objectMapper) {
        this.modelPort = modelPort;
        this.objectMapper = objectMapper;
    }

    public List<Finding> review(
            String documentTypeCode, List<DocumentParagraphEntity> paragraphs, String securityLevel, String taskId) {
        ModelGenerationPort port = modelPort.getIfAvailable();
        String dataLevel = dataLevel(securityLevel);
        if (port == null || dataLevel == null || paragraphs.isEmpty()) return List.of();
        Optional<ModelGenerationPort.Result> response;
        try {
            response = port.generate(SCENE, dataLevel, documentTypeCode, render(paragraphs), Map.of(), "审校", taskId);
        } catch (RuntimeException e) {
            log.warn("模型辅助逻辑审校调用失败，已跳过：{}", e.getMessage());
            return List.of();
        }
        return response.map(result -> parse(result.output(), paragraphs)).orElse(List.of());
    }

    List<Finding> parse(String output, List<DocumentParagraphEntity> paragraphs) {
        if (!StringUtils.hasText(output)) return List.of();
        int open = output.indexOf('[');
        int close = output.lastIndexOf(']');
        if (open < 0 || close <= open) return List.of();
        JsonNode items;
        try {
            items = objectMapper.readTree(output.substring(open, close + 1));
        } catch (Exception e) {
            log.warn("模型辅助逻辑审校输出不是合法 JSON，已丢弃");
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (JsonNode item : items) {
            if (findings.size() >= MAX_FINDINGS) break;
            int number = item.path("paragraph").asInt(-1);
            String quote = item.path("quote").asText("").trim();
            DocumentParagraphEntity paragraph = paragraphs.stream()
                    .filter(p -> p.getParagraphOrder() != null && p.getParagraphOrder() == number)
                    .findFirst()
                    .orElse(null);
            if (paragraph == null || quote.isEmpty() || paragraph.getPlainText() == null) continue;
            int start = paragraph.getPlainText().indexOf(quote);
            if (start < 0) continue;
            String explanation = "问题类型：" + text(item, "type", "逻辑问题") + "；判断理由：" + text(item, "reason", "未说明")
                    + "；建议修改方向：" + text(item, "suggestion", "请人工判断") + "。（模型提出，须人工确认）";
            findings.add(new Finding(
                    "L6",
                    paragraph,
                    "逻辑语义",
                    "提示",
                    start,
                    start + quote.length(),
                    quote,
                    explanation,
                    "模型辅助逻辑审校（§23）",
                    new BigDecimal("0.60")));
        }
        return findings;
    }

    /** 文稿密级映射到模型网关数据级别；涉密文稿不送模型。 */
    static String dataLevel(String securityLevel) {
        if (securityLevel == null) return "内部";
        return switch (securityLevel) {
            case "公开", "普通" -> "普通";
            case "敏感" -> "敏感";
            case "涉密", "秘密", "机密", "绝密" -> null;
            default -> "内部";
        };
    }

    private static String render(List<DocumentParagraphEntity> paragraphs) {
        StringBuilder input = new StringBuilder("请检查以下文稿中的前后矛盾、逻辑跳跃、因果不当和风险表述，不要把写作风格差异判为逻辑错误。"
                + "仅输出 JSON 数组，每项为 {\"paragraph\":段落编号,\"quote\":\"原文中逐字摘录的问题片段\","
                + "\"type\":\"问题类型\",\"reason\":\"判断理由\",\"suggestion\":\"建议修改方向\"}；没有问题时输出 []。\n");
        for (DocumentParagraphEntity paragraph : paragraphs) {
            input.append("[段落")
                    .append(paragraph.getParagraphOrder())
                    .append("] ")
                    .append(paragraph.getPlainText() == null ? "" : paragraph.getPlainText())
                    .append('\n');
        }
        return input.toString();
    }

    private static String text(JsonNode item, String field, String fallback) {
        String value = item.path(field).asText("").trim();
        return value.isEmpty() ? fallback : value;
    }
}
