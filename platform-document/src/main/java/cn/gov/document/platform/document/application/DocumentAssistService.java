package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.model.ModelGenerationPort;
import cn.gov.document.platform.document.api.DocumentDtos.AssistApplyRequest;
import cn.gov.document.platform.document.api.DocumentDtos.AssistPreviewRequest;
import cn.gov.document.platform.document.api.DocumentDtos.AssistPreviewResponse;
import cn.gov.document.platform.document.api.DocumentDtos.AssistProposal;
import cn.gov.document.platform.document.api.DocumentDtos.DocumentResponse;
import cn.gov.document.platform.document.api.DocumentDtos.FormatChange;
import cn.gov.document.platform.document.api.DocumentDtos.FormatPreviewResponse;
import cn.gov.document.platform.document.api.DocumentDtos.OpenWritingRequest;
import cn.gov.document.platform.document.api.DocumentDtos.SummaryResponse;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.writing.application.WritingMaterialService;
import cn.gov.document.platform.writing.application.WritingTaskService;
import cn.gov.document.platform.writing.domain.WritingOutlineEntity;
import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import cn.gov.document.platform.writing.persistence.WritingOutlineMapper;
import cn.gov.document.platform.writing.persistence.WritingOutlineNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DocumentAssistService {
    private static final int MAX_PARAGRAPHS = 12;
    private static final String[] CN = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,2})[、.．](.+)$");

    private final DocumentService documents;
    private final WritingTaskService tasks;
    private final WritingMaterialService materials;
    private final RuleBasedDocumentGenerator generator;
    private final ObjectProvider<ModelGenerationPort> modelPort;
    private final WritingOutlineMapper outlineMapper;
    private final WritingOutlineNodeMapper nodeMapper;

    public DocumentAssistService(
            DocumentService documents,
            WritingTaskService tasks,
            WritingMaterialService materials,
            RuleBasedDocumentGenerator generator,
            ObjectProvider<ModelGenerationPort> modelPort,
            WritingOutlineMapper outlineMapper,
            WritingOutlineNodeMapper nodeMapper) {
        this.documents = documents;
        this.tasks = tasks;
        this.materials = materials;
        this.generator = generator;
        this.modelPort = modelPort;
        this.outlineMapper = outlineMapper;
        this.nodeMapper = nodeMapper;
    }

    public SummaryResponse summary(String taskId) {
        WritingTaskEntity task = tasks.require(taskId);
        return summarize(
                body(documents.currentParagraphs(documents.documentOf(taskId))),
                task.getDocumentTypeCode(),
                task.getSecurityLevel(),
                task.getId());
    }

    public SummaryResponse openSummary(OpenWritingRequest request) {
        if (StringUtils.hasText(request.taskId())) return summary(request.taskId());
        if (!StringUtils.hasText(request.text())) throw BusinessException.unprocessable("请提供 taskId 或 text");
        return summarize(request.text().strip(), "通知", "内部", "open");
    }

    public AssistPreviewResponse preview(String taskId, AssistPreviewRequest request) {
        WritingTaskEntity task = tasks.require(taskId);
        assertExpandScope(taskId, request.operation(), request.scope());
        List<DocumentParagraphEntity> source =
                select(documents.currentParagraphs(documents.documentOf(taskId)), request);
        if (source.isEmpty()) throw BusinessException.unprocessable("当前范围没有可处理的段落");
        return propose(task, request.operation(), request.scope(), source, outlineHint(request.outlineNodeId()), null);
    }

    public AssistPreviewResponse openPreview(String operation, OpenWritingRequest request) {
        if (StringUtils.hasText(request.taskId())) {
            String scope = StringUtils.hasText(request.scope()) ? request.scope() : "全文";
            return preview(request.taskId(), new AssistPreviewRequest(operation, scope, request.outlineNodeId()));
        }
        if (!StringUtils.hasText(request.text())) throw BusinessException.unprocessable("请提供 taskId 或 text");
        if ("本节".equals(request.scope())) throw BusinessException.unprocessable("纯文本请使用全文");
        List<DocumentParagraphEntity> source = new ArrayList<>();
        int order = 1;
        for (String line : request.text().strip().split("\\n+")) {
            if (!StringUtils.hasText(line)) continue;
            DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
            paragraph.setId(String.valueOf(order));
            paragraph.setParagraphOrder(order++);
            paragraph.setPlainText(line.strip());
            paragraph.setIsLocked(false);
            source.add(paragraph);
        }
        if (source.isEmpty()) throw BusinessException.unprocessable("正文为空");
        return propose(null, operation, "全文", source, "", null);
    }

    public SummaryResponse summarizeSubmitted(String text) {
        return summarize(text, DocumentTypeDetector.resolve(null, text), "内部", "open");
    }

    public AssistPreviewResponse rewriteSubmitted(String operation, String text) {
        if (!StringUtils.hasText(text)) throw BusinessException.unprocessable("请提供正文");
        List<DocumentParagraphEntity> source = new ArrayList<>();
        int order = 1;
        for (String line : text.strip().split("\\n+")) {
            if (!StringUtils.hasText(line)) continue;
            DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
            paragraph.setId(String.valueOf(order));
            paragraph.setParagraphOrder(order++);
            paragraph.setPlainText(line.strip());
            paragraph.setIsLocked(false);
            source.add(paragraph);
        }
        if (source.isEmpty()) throw BusinessException.unprocessable("正文为空");
        return propose(null, operation, "全文", source, "", DocumentTypeDetector.resolve(null, text));
    }

    public DocumentResponse apply(String taskId, AssistApplyRequest request) {
        tasks.require(taskId);
        Map<String, String> texts = new LinkedHashMap<>();
        request.items().forEach(item -> texts.put(item.paragraphId(), item.text()));
        return documents.rewriteParagraphs(taskId, texts, "AI改写", "采用段落润色或扩写", true);
    }

    public DocumentResponse insertSummary(String taskId, String text) {
        tasks.require(taskId);
        return documents.insertLeadingParagraph(taskId, text, "插入文首摘要");
    }

    public FormatPreviewResponse formatPreview(String taskId) {
        tasks.require(taskId);
        List<DocumentParagraphEntity> paragraphs = documents.currentParagraphs(documents.documentOf(taskId));
        if (paragraphs.isEmpty()) throw BusinessException.unprocessable("尚无正文");
        List<FormatChange> changes = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            DocumentParagraphEntity paragraph = paragraphs.get(i);
            if (Boolean.TRUE.equals(paragraph.getIsLocked()) || !StringUtils.hasText(paragraph.getPlainText()))
                continue;
            Tidied tidied = tidy(paragraph.getPlainText(), i == 0, i == paragraphs.size() - 1);
            if (!tidied.text().equals(paragraph.getPlainText().strip()))
                changes.add(new FormatChange(
                        paragraph.getId(),
                        paragraph.getParagraphOrder(),
                        paragraph.getPlainText(),
                        tidied.text(),
                        tidied.reason()));
        }
        return new FormatPreviewResponse(changes);
    }

    public DocumentResponse formatApply(String taskId) {
        FormatPreviewResponse preview = formatPreview(taskId);
        if (preview.changes().isEmpty()) throw BusinessException.unprocessable("没有需要整理的内容");
        Map<String, String> texts = new LinkedHashMap<>();
        preview.changes().forEach(change -> texts.put(change.paragraphId(), change.proposed()));
        return documents.rewriteParagraphs(taskId, texts, "格式整理", "确认格式整理", false);
    }

    public void requireBusiness(String taskId, String businessId) {
        WritingTaskEntity task = tasks.require(taskId);
        if (!StringUtils.hasText(businessId) || !businessId.equals(task.getBusinessId()))
            throw BusinessException.notFound("写作任务不存在");
    }

    private SummaryResponse summarize(String text, String documentType, String security, String taskId) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) throw BusinessException.unprocessable("尚无正文");
        if (body.length() > 20000) body = body.substring(0, 20000);
        String input = "只根据正文提取摘要，不要补充正文没有的事实、数字和政策。\n输出格式：\n摘要：一句话\n要点：\n- 要点\n\n正文：\n" + body;
        String generated =
                call("文本改写", documentType, security, taskId, input, Map.of("operation", "摘要", "source", body));
        if (generated == null) return fallbackSummary(body);
        return parseSummary(generated);
    }

    private AssistPreviewResponse propose(
            WritingTaskEntity task,
            String operation,
            String scope,
            List<DocumentParagraphEntity> source,
            String hint,
            String documentType) {
        List<AssistProposal> proposals = new ArrayList<>();
        boolean truncated = false;
        String materialsText = task == null
                ? "未提供可用材料，禁止补造事实依据。"
                : materials.prepareGenerationContext(task.getId()).text();
        for (DocumentParagraphEntity paragraph : source) {
            if (Boolean.TRUE.equals(paragraph.getIsLocked())) continue;
            String original = paragraph.getPlainText() == null
                    ? ""
                    : paragraph.getPlainText().strip();
            if (original.isEmpty()) continue;
            if (proposals.size() >= MAX_PARAGRAPHS) {
                truncated = true;
                break;
            }
            String proposed = rewrite(task, operation, original, hint, materialsText, documentType);
            if (!StringUtils.hasText(proposed) || proposed.equals(original)) continue;
            proposals.add(new AssistProposal(
                    paragraph.getId(),
                    paragraph.getParagraphOrder() == null ? proposals.size() + 1 : paragraph.getParagraphOrder(),
                    original,
                    proposed));
        }
        return new AssistPreviewResponse(operation, scope, proposals, truncated);
    }

    private String rewrite(
            WritingTaskEntity task,
            String operation,
            String original,
            String hint,
            String materialsText,
            String documentType) {
        String limit = "润色".equals(operation) ? "只改表述，不改事实、数字、时间和政策口径。" : "只在本段内补充分论点、依据和措施，不新增章节，不改已有事实和数字。";
        String input = "操作：" + operation + "。输出可直接替换该段的正文，不要解释。\n" + limit
                + (hint.isBlank() ? "" : "\n章节：" + hint)
                + "\n原文：" + original + "\n\n" + materialsText;
        String type = task == null ? documentType(documentType) : task.getDocumentTypeCode();
        String generated = call(
                "文本改写",
                type,
                task == null ? "内部" : task.getSecurityLevel(),
                task == null ? "open" : task.getId(),
                input,
                Map.of("operation", operation, "source", original));
        if (generated == null) {
            String rule = "扩写".equals(operation) ? "扩写" : "改写";
            WritingTaskEntity fallbackTask = task == null ? bareTask(type) : task;
            generated = generator.transform(rule, fallbackTask, original, hint, null);
        }
        return clean(generated);
    }

    private String call(
            String scene,
            String documentType,
            String security,
            String taskId,
            String input,
            Map<String, Object> variables) {
        ModelGenerationPort port = modelPort.getIfAvailable();
        if (port == null) return null;
        try {
            var result = port.generate(scene, dataLevel(security), documentType, input, variables, "写作", taskId);
            if (result.isEmpty() || !StringUtils.hasText(result.get().output())) return null;
            return result.get().output().strip();
        } catch (BusinessException exception) {
            return null;
        }
    }

    private void assertExpandScope(String taskId, String operation, String scope) {
        if (!"扩写".equals(operation) || !"全文".equals(scope)) return;
        WritingOutlineEntity outline = latestOutline(taskId);
        if (outline == null || !Boolean.TRUE.equals(outline.getIsConfirmed()))
            throw BusinessException.unprocessable("全文扩写需要已确认提纲，请改为扩写当前节");
    }

    private List<DocumentParagraphEntity> select(
            List<DocumentParagraphEntity> paragraphs, AssistPreviewRequest request) {
        if ("全文".equals(request.scope())) return paragraphs;
        if (!StringUtils.hasText(request.outlineNodeId())) throw BusinessException.unprocessable("请指定章节");
        return paragraphs.stream()
                .filter(item -> request.outlineNodeId().equals(item.getOutlineNodeId()))
                .toList();
    }

    private String outlineHint(String outlineNodeId) {
        if (!StringUtils.hasText(outlineNodeId)) return "";
        WritingOutlineNodeEntity node = nodeMapper.selectById(outlineNodeId);
        if (node == null) return "";
        return (node.getNodeTitle() == null ? "" : node.getNodeTitle())
                + (node.getWritingPurpose() == null ? "" : " " + node.getWritingPurpose());
    }

    private WritingOutlineEntity latestOutline(String taskId) {
        return outlineMapper.selectOne(new LambdaQueryWrapper<WritingOutlineEntity>()
                .eq(WritingOutlineEntity::getTaskId, taskId)
                .orderByDesc(WritingOutlineEntity::getOutlineVersion)
                .last("LIMIT 1"));
    }

    private static SummaryResponse fallbackSummary(String body) {
        List<String> points = new ArrayList<>();
        for (String line : body.split("\\n+")) {
            if (!StringUtils.hasText(line)) continue;
            points.add(clip(line));
            if (points.size() == 5) break;
        }
        String brief = points.isEmpty() ? clip(body) : points.get(0);
        return new SummaryResponse(brief, points, "rule-based-fallback");
    }

    private static String clip(String line) {
        String value = line.strip().replace('\n', ' ');
        int end = value.indexOf('。');
        if (end >= 0 && end <= 80) return value.substring(0, end + 1);
        if (value.codePointCount(0, value.length()) <= 40) return value;
        return value.substring(0, value.offsetByCodePoints(0, 40));
    }

    private static SummaryResponse parseSummary(String output) {
        String brief = "";
        List<String> points = new ArrayList<>();
        for (String line : output.split("\\n")) {
            String row = line.strip();
            if (row.startsWith("摘要")) brief = row.replaceFirst("^摘要[:：]\\s*", "");
            else if (row.matches("^[\\-•].*") || row.matches("^\\d+[.、].*"))
                points.add(row.replaceFirst("^[\\-•\\d.、\\s]+", ""));
        }
        if (!StringUtils.hasText(brief)) brief = output;
        return new SummaryResponse(
                brief.strip(), points.stream().filter(StringUtils::hasText).toList(), "model");
    }

    private static String clean(String output) {
        String value = output == null ? "" : output.strip();
        value = value.replaceFirst("^(润色后|扩写后|改写后|输出)[:：]\\s*", "");
        if (value.startsWith("```"))
            value = value.replaceAll("^```[a-zA-Z]*\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .strip();
        return value;
    }

    private static WritingTaskEntity bareTask(String documentType) {
        WritingTaskEntity task = new WritingTaskEntity();
        task.setDocumentTypeCode(documentType(documentType));
        task.setWritingTopic("正文");
        return task;
    }

    private static String documentType(String code) {
        return StringUtils.hasText(code) ? code.trim() : "通知";
    }

    private static String dataLevel(String value) {
        return switch (value == null ? "" : value) {
            case "普通", "公开" -> "普通";
            case "敏感", "秘密" -> "敏感";
            case "涉密", "机密" -> "涉密";
            default -> "内部";
        };
    }

    private String body(List<DocumentParagraphEntity> paragraphs) {
        return paragraphs.stream()
                .map(DocumentParagraphEntity::getPlainText)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private record Tidied(String text, String reason) {}

    private static Tidied tidy(String source, boolean first, boolean last) {
        String value = source.strip();
        List<String> reasons = new ArrayList<>();
        if (first && value.length() <= 80 && value.endsWith("。")) {
            value = value.substring(0, value.length() - 1);
            reasons.add("标题去掉句号");
        }
        if (value.matches("^[\\u4e00-\\u9fa5A-Za-z0-9、（）()]{2,40}:$")) {
            value = value.substring(0, value.length() - 1) + "：";
            reasons.add("主送改用全角冒号");
        }
        Matcher numbered = NUMBERED.matcher(value);
        if (numbered.matches()) {
            int number = Integer.parseInt(numbered.group(1));
            if (number >= 1 && number <= 10) {
                value = CN[number] + "、" + numbered.group(2).strip();
                reasons.add("层次序号改为汉字");
            }
        }
        if (last) {
            String dated = value.replaceAll("([12]\\d{3})年0([1-9])月", "$1年$2月").replaceAll("月0([1-9])日", "月$1日");
            if (!dated.equals(value)) {
                value = dated;
                reasons.add("落款日期去掉前导零");
            }
        }
        return new Tidied(value, String.join("，", reasons));
    }
}
