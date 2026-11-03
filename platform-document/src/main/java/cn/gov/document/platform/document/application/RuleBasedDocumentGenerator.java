package cn.gov.document.platform.document.application;

import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuleBasedDocumentGenerator {
    public String generate(WritingTaskEntity task, WritingOutlineNodeEntity node, String instruction) {
        String topic = task.getWritingTopic();
        String section = node.getNodeTitle();
        String extra = StringUtils.hasText(instruction) ? "写作要求：" + instruction.trim() + "。" : "";
        String body;
        if (contains(section, "背景", "概况", "总体", "基本情况", "现状")) {
            body = "围绕“" + topic + "”，本节应交代工作背景、当前基础和目标范围。具体时间、数据及政策依据请依据核实后的材料补充。";
        } else if (contains(section, "主要工作", "主要内容", "任务", "举措", "措施", "实施")) {
            body = "围绕“" + topic + "”，本节应分项说明已开展或拟开展的重点工作，明确责任主体、实施方式和完成标准。具体项目与成效请依据核实后的材料补充。";
        } else if (contains(section, "问题", "困难", "风险", "不足", "分析")) {
            body = "围绕“" + topic + "”，本节应分析当前存在的主要问题、形成原因及可能影响。问题表现和影响程度请依据核实后的材料补充，避免无据判断。";
        } else if (contains(section, "下一步", "后续", "安排", "计划", "保障")) {
            body = "下一步，围绕“" + topic + "”明确重点任务、责任分工、完成时限和跟踪机制。具体安排应与已确认的计划及审批意见保持一致。";
        } else if (contains(section, "请示事项", "请求", "审批")) {
            body = "本节应明确“" + topic + "”所需批准的单一事项、实施范围和请求内容；金额、期限等关键要素请依据核实后的材料填写。";
        } else {
            body = "“" + section + "”部分围绕“" + topic + "”展开，应按提纲要求补充该节专属事实、依据与措施，并核对相关材料。";
        }
        return switch (task.getDocumentTypeCode()) {
            case "请示" -> body + (contains(section, "请求", "请示事项", "审批") ? "以上事项妥否，请批示。" : "") + extra;
            case "通知", "报告" -> body + extra;
            default -> body + extra;
        };
    }

    private boolean contains(String value, String... keywords) {
        if (value == null) return false;
        for (String keyword : keywords) if (value.contains(keyword)) return true;
        return false;
    }

    public String transform(
            String generationType, WritingTaskEntity task, String source, String instruction, Integer targetWordCount) {
        String text = source == null ? "" : source.trim();
        return switch (generationType) {
            case "续写" -> continuation(task, instruction);
            case "改写" -> formalize(text, instruction);
            case "压缩" -> compress(
                    text,
                    targetWordCount == null
                            ? Math.max(20, text.codePointCount(0, text.length()) * 2 / 3)
                            : targetWordCount);
            case "扩写" -> expand(text, task, instruction, targetWordCount);
            default -> throw new IllegalArgumentException("不支持的生成类型");
        };
    }

    private String continuation(WritingTaskEntity task, String instruction) {
        String extra = StringUtils.hasText(instruction) ? "重点落实“" + instruction.trim() + "”要求。" : "";
        return switch (task.getDocumentTypeCode()) {
            case "请示" -> "为保障上述事项顺利实施，将进一步细化资金测算、责任分工和风险控制措施。" + extra;
            case "通知" -> "各责任单位要结合实际制定落实方案，明确时间节点，并及时报送工作进展。" + extra;
            case "报告" -> "下一步，将持续跟踪有关情况，完善工作机制，确保各项任务取得实效。" + extra;
            default -> "下一步，将围绕既定目标细化措施、压实责任并跟踪实施成效。" + extra;
        };
    }

    private String formalize(String text, String instruction) {
        String value = text.replace("马上", "及时")
                .replace("搞好", "做好")
                .replace("做出贡献", "作出贡献")
                .replace("截止目前", "截至目前");
        if (StringUtils.hasText(instruction) && instruction.contains("逻辑") && !value.startsWith("为此"))
            value = "为此，" + value;
        return value;
    }

    private String compress(String text, int target) {
        if (text.codePointCount(0, text.length()) <= target) return text;
        int end = text.offsetByCodePoints(0, target);
        String value = text.substring(0, end).replaceAll("[，、；：]$", "");
        return value.matches(".*[。！？]$") ? value : value + "。";
    }

    private String expand(String text, WritingTaskEntity task, String instruction, Integer target) {
        String addition = "同时，要坚持依法依规、实事求是，进一步明确责任主体、工作标准和完成时限，强化过程监督与风险防控。";
        if (StringUtils.hasText(instruction)) addition += "具体按照“" + instruction.trim() + "”要求抓好落实。";
        String value = text + (text.matches(".*[。！？]$") ? "" : "。") + addition;
        int desired = target == null ? value.codePointCount(0, value.length()) : target;
        while (value.codePointCount(0, value.length()) < desired)
            value += "围绕" + task.getWritingTopic() + "，及时总结实施情况并动态完善相关措施。";
        return value;
    }
}
