package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.writing.api.WritingOutlineDtos.NodeDraft;
import cn.gov.document.platform.writing.api.WritingPlanningDtos.TitleCandidate;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleBasedWritingAssistant implements WritingAssistant {
    private final DocumentTypeConfigService configService;

    public RuleBasedWritingAssistant(DocumentTypeConfigService configService) {
        this.configService = configService;
    }

    @Override
    public List<DocumentTypeSuggestion> recommendDocumentTypes(String purpose) {
        String value = purpose == null ? "" : purpose;
        if (value.contains("申请") || value.contains("批准") || value.contains("请示")) {
            return List.of(new DocumentTypeSuggestion("请示", "向上级机关请求指示或批准，适用请示且应一文一事。", 0.92));
        }
        if (value.contains("汇报") || value.contains("进展") || value.contains("情况")) {
            return List.of(
                new DocumentTypeSuggestion("报告", "向上级机关汇报工作或反映情况。", 0.86),
                new DocumentTypeSuggestion("情况说明", "用于解释具体事实或背景。", 0.68));
        }
        if (value.contains("部署") || value.contains("安排") || value.contains("要求")) {
            return List.of(new DocumentTypeSuggestion("通知", "用于发布、传达要求并部署工作。", 0.82));
        }
        return List.of(
            new DocumentTypeSuggestion("函", "适合平行机关之间商洽、询问或答复事项。", 0.55),
            new DocumentTypeSuggestion("报告", "适合汇报情况并提出工作建议。", 0.51));
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
        String topic=task.getWritingTopic(); String type=task.getDocumentTypeCode();
        List<String> sequence="请示".equals(type)
            ? List.of("交代事项背景与政策依据","说明现实必要性和可行条件","明确唯一请示事项","提出保障措施并使用规范结束语")
            : List.of("概述背景与总体目标","陈述事实与主要问题","提出工作举措","明确责任与后续安排");
        return new IdeaDraft("围绕“"+topic+"”形成事实充分、请求或措施明确的"+type,
            List.of("坚持材料有据、事实可核验","突出核心事项，避免多主题并列","明确责任主体、实施范围和完成标准"), sequence,
            "必须核实发文依据、关键数据、责任主体、时间节点和行文对象。",
            "可根据材料充分程度补充成效、案例、风险应对和附件说明。",
            materialCount==0?"尚未添加材料；生成正文前至少补充一份可核验依据。":"已关联 "+materialCount+" 份材料；仍需人工确认其时效性与权限。",
            "不得虚构政策依据、统计数据或审批结论；请核对文种适用性和保密要求。");
    }

    @Override
    public List<TitleCandidate> generateTitleCandidates(WritingTaskEntity task) {
        String authority=task.getPrimaryRecipient()==null||task.getPrimaryRecipient().isBlank()?"本单位":task.getPrimaryRecipient();
        String subject=task.getWritingTopic(); String type=task.getDocumentTypeCode();
        String standard=authority+"关于"+subject+"的"+type;
        return List.of(
            new TitleCandidate(standard,"发文机关＋事由＋文种","正式发文标题",true,true,true,List.of(),0.94),
            new TitleCandidate("关于"+subject+"的"+type,"事由＋文种","发文机关在版记或系统元数据中明确时使用",false,true,true,List.of("正式导出前确认是否必须补充发文机关"),0.88),
            new TitleCandidate(subject+"工作"+type,"事由＋文种","内部流转或工作稿",false,true,true,List.of("表意较宽，需确认是否准确覆盖核心事项"),0.73));
    }
}
