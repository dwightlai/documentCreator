package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeDraft;
import cn.gov.document.platform.writing.api.WritingPlanningDtos.TitleCandidate;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedWritingAssistant implements WritingAssistant {
    private final DocumentTypeConfigService configService;

    public RuleBasedWritingAssistant(DocumentTypeConfigService configService) {
        this.configService = configService;
    }

    private static final List<String> STATUTORY_TYPES =
            List.of("决议", "决定", "命令", "公报", "公告", "通告", "意见", "通报", "通知", "报告", "请示", "批复", "议案", "函", "纪要");

    @Override
    public List<DocumentTypeSuggestion> recommendDocumentTypes(WritingTaskEntity task) {
        String titled = statutoryTitle(task.getTaskName(), task.getWritingTopic());
        if (titled != null) return List.of(fromTitle(titled));
        DocumentTypeSuggestion byPurpose = fromPurpose(task.getWritingPurpose());
        if (byPurpose != null) return List.of(byPurpose);
        return List.of(new DocumentTypeSuggestion("未能判定", "标题未标明法定文种，写作目的也不足以对照条例第八条确定文种。", 0));
    }

    private static String statutoryTitle(String... candidates) {
        if (candidates == null) return null;
        for (String candidate : candidates) {
            if (candidate != null && statutoryType(candidate.strip()) != null) return candidate.strip();
        }
        return null;
    }

    private static String statutoryType(String text) {
        if (text == null || text.isBlank()) return null;
        if (text.endsWith("会议纪要")) return "纪要";
        for (String type : STATUTORY_TYPES) {
            if (text.endsWith("的" + type) || text.endsWith("的" + type + "）") || text.endsWith("的" + type + ")")) return type;
        }
        return null;
    }

    private static DocumentTypeSuggestion fromTitle(String title) {
        String type = statutoryType(title);
        if ("通知".equals(type) && title.contains("印发")) {
            return new DocumentTypeSuggestion(
                    "通知",
                    "标题以“的通知”结尾，事由是印发本机关制定的方案、办法、细则或计划。法定文种是通知，属于发布性通知中的印发类通知。书名号内的文件名称不是法定文种。",
                    0.96);
        }
        if ("通知".equals(type) && title.contains("批转")) {
            return new DocumentTypeSuggestion("通知", "标题以“的通知”结尾，事由是批转下级机关公文。法定文种是通知，属于批转性通知。", 0.96);
        }
        if ("通知".equals(type) && title.contains("转发")) {
            return new DocumentTypeSuggestion("通知", "标题以“的通知”结尾，事由是转发上级机关或不相隶属机关的公文。法定文种是通知，属于转发性通知。", 0.96);
        }
        if ("通知".equals(type)) {
            return new DocumentTypeSuggestion("通知", "标题以“的通知”结尾。通知适用于发布、传达要求下级机关执行和有关单位周知或者执行的事项。", 0.94);
        }
        return new DocumentTypeSuggestion(type, "标题以“的" + type + "”结尾，按标题标明的法定文种确定。", 0.94);
    }

    private static DocumentTypeSuggestion fromPurpose(String purpose) {
        String value = purpose == null ? "" : purpose;
        if (value.contains("批复") || (value.contains("答复") && value.contains("请示"))) {
            return new DocumentTypeSuggestion("批复", "适用于答复下级机关的请示事项。", 0.9);
        }
        if (value.contains("请示") || (value.contains("上级") && (value.contains("申请") || value.contains("批准") || value.contains("指示")))) {
            return new DocumentTypeSuggestion("请示", "适用于向上级机关请求指示或批准，一文一事。", 0.9);
        }
        if (value.contains("汇报") || value.contains("反映情况") || (value.contains("上级") && value.contains("询问"))) {
            return new DocumentTypeSuggestion("报告", "适用于向上级机关汇报工作、反映情况，或回复上级机关的询问。", 0.9);
        }
        if (value.contains("表彰") || value.contains("批评")) {
            return new DocumentTypeSuggestion("通报", "适用于表彰先进、批评错误、传达重要精神和告知重要情况。", 0.9);
        }
        if (value.contains("纪要") || value.contains("议定")) {
            return new DocumentTypeSuggestion("纪要", "适用于记载会议主要情况和议定事项。", 0.9);
        }
        if (value.contains("商洽") || value.contains("不相隶属") || value.contains("平行")) {
            return new DocumentTypeSuggestion("函", "适用于不相隶属机关之间商洽工作、询问和答复问题、请求批准和答复审批事项。", 0.9);
        }
        if (value.contains("印发") || value.contains("批转") || value.contains("转发") || value.contains("发布") || value.contains("传达") || value.contains("周知") || value.contains("部署") || value.contains("安排")) {
            return new DocumentTypeSuggestion("通知", "适用于发布、传达要求下级机关执行和有关单位周知或者执行的事项，以及批转、转发公文。", 0.9);
        }
        return null;
    }

    @Override
    public List<NodeDraft> generateOutline(WritingTaskEntity task, int materialCount) {
        int total = task.getExpectedLengthMax() == null ? 2400 : task.getExpectedLengthMax();
        String type = task.getDocumentTypeCode();
        var effective = configService.effective(type);
        if (effective.isPresent() && !effective.get().recommendedStructure().isEmpty()) {
            List<String> structure = effective.get().recommendedStructure();
            int base = Math.max(1, total / structure.size());
            java.util.ArrayList<NodeDraft> drafts = new java.util.ArrayList<>();
            for (int index = 0; index < structure.size(); index++) {
                int target = index == structure.size() - 1 ? total - base * index : base;
                String purpose = "按已发布文种业务配置完成“" + structure.get(index) + "”要素";
                if (index == 0) purpose += "，可用材料数：" + materialCount;
                drafts.add(new NodeDraft(structure.get(index), purpose, Math.max(target, 1), true));
            }
            return List.copyOf(drafts);
        }
        if ("请示".equals(type)) {
            return List.of(
                    new NodeDraft("请示背景与依据", "说明事项背景、政策依据和现实必要性。材料数：" + materialCount, total * 35 / 100, true),
                    new NodeDraft("具体请示事项", "明确一项核心请求及实施范围。", total * 40 / 100, true),
                    new NodeDraft("保障措施与请求", "说明条件、风险控制并使用规范结束语。", total * 25 / 100, true));
        }
        if ("通知".equals(type)) {
            return List.of(
                    new NodeDraft("工作背景与总体要求", "交代依据、目的和总体原则。", total * 25 / 100, true),
                    new NodeDraft("主要任务", "分项说明工作任务、责任主体和完成标准。", total * 50 / 100, true),
                    new NodeDraft("组织实施", "明确时限、协同和监督要求。", total * 25 / 100, true));
        }
        return List.of(
                new NodeDraft("基本情况", "概述背景、范围与材料依据。", total * 30 / 100, true),
                new NodeDraft("主要内容", "围绕主题展开事实、分析与举措。", total * 45 / 100, true),
                new NodeDraft("下一步安排", "形成结论并明确后续工作。", total * 25 / 100, true));
    }

    @Override
    public IdeaDraft generateIdea(WritingTaskEntity task, int materialCount) {
        String topic = task.getWritingTopic();
        String type = task.getDocumentTypeCode();
        List<String> sequence = "请示".equals(type)
                ? List.of("交代事项背景与政策依据", "说明现实必要性和可行条件", "明确唯一请示事项", "提出保障措施并使用规范结束语")
                : List.of("概述背景与总体目标", "陈述事实与主要问题", "提出工作举措", "明确责任与后续安排");
        return new IdeaDraft(
                "围绕“" + topic + "”形成事实充分、请求或措施明确的" + type,
                List.of("坚持材料有据、事实可核验", "突出核心事项，避免多主题并列", "明确责任主体、实施范围和完成标准"),
                sequence,
                "必须核实发文依据、关键数据、责任主体、时间节点和行文对象。",
                "可根据材料充分程度补充成效、案例、风险应对和附件说明。",
                materialCount == 0 ? "尚未添加材料；生成正文前至少补充一份可核验依据。" : "已关联 " + materialCount + " 份材料；仍需人工确认其时效性与权限。",
                "不得虚构政策依据、统计数据或审批结论；请核对文种适用性和保密要求。");
    }

    @Override
    public List<TitleCandidate> generateTitleCandidates(WritingTaskEntity task) {
        String authority =
                task.getPrimaryRecipient() == null || task.getPrimaryRecipient().isBlank()
                        ? "本单位"
                        : task.getPrimaryRecipient();
        String subject = task.getWritingTopic().trim();
        if (subject.startsWith("关于")) subject = subject.substring(2);
        String type = task.getDocumentTypeCode();
        if (subject.endsWith("的" + type)) subject = subject.substring(0, subject.length() - type.length() - 1);
        if (subject.endsWith(type)) subject = subject.substring(0, subject.length() - type.length());
        String standard = authority + "关于" + subject + "的" + type;
        return List.of(
                new TitleCandidate(standard, "发文机关＋事由＋文种", "正式发文标题", true, true, true, List.of(), 0.94),
                new TitleCandidate(
                        "关于" + subject + "的" + type,
                        "事由＋文种",
                        "发文机关在版记或系统元数据中明确时使用",
                        false,
                        true,
                        true,
                        List.of("正式导出前确认是否必须补充发文机关"),
                        0.88),
                new TitleCandidate(
                        subject + "工作" + type,
                        "事由＋文种",
                        "内部流转或工作稿",
                        false,
                        true,
                        true,
                        List.of("表意较宽，需确认是否准确覆盖核心事项"),
                        0.73));
    }
}
