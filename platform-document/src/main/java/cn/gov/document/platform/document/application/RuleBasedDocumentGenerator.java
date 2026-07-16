package cn.gov.document.platform.document.application;

import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuleBasedDocumentGenerator {
    public String generate(WritingTaskEntity task, WritingOutlineNodeEntity node, String instruction) {
        String topic = task.getWritingTopic();
        String purpose = node.getWritingPurpose();
        String extra = StringUtils.hasText(instruction) ? "写作要求：" + instruction.trim() + "。" : "";
        return switch (task.getDocumentTypeCode()) {
            case "请示" -> "根据有关工作要求，现就" + topic + "有关事项说明如下。" + purpose
                + "为确保工作依法依规、稳妥有序推进，恳请对上述事项予以研究批复。" + extra;
            case "通知" -> "为切实做好" + topic + "，现将有关事项通知如下。" + purpose
                + "各责任单位要强化组织领导，明确责任分工，按期完成各项任务。" + extra;
            case "报告" -> "现将" + topic + "有关情况报告如下。" + purpose
                + "下一步，将持续跟踪工作进展，及时研究解决实施中的问题。" + extra;
            default -> "围绕" + topic + "，本节重点说明以下内容。" + purpose + extra;
        };
    }

    public String transform(String generationType, WritingTaskEntity task, String source, String instruction, Integer targetWordCount) {
        String text=source==null?"":source.trim();
        return switch (generationType) {
            case "续写" -> continuation(task, instruction);
            case "改写" -> formalize(text, instruction);
            case "压缩" -> compress(text, targetWordCount==null?Math.max(20,text.codePointCount(0,text.length())*2/3):targetWordCount);
            case "扩写" -> expand(text, task, instruction, targetWordCount);
            default -> throw new IllegalArgumentException("不支持的生成类型");
        };
    }

    private String continuation(WritingTaskEntity task,String instruction){
        String extra=StringUtils.hasText(instruction)?"重点落实“"+instruction.trim()+"”要求。":"";
        return switch(task.getDocumentTypeCode()){
            case "请示" -> "为保障上述事项顺利实施，将进一步细化资金测算、责任分工和风险控制措施。"+extra;
            case "通知" -> "各责任单位要结合实际制定落实方案，明确时间节点，并及时报送工作进展。"+extra;
            case "报告" -> "下一步，将持续跟踪有关情况，完善工作机制，确保各项任务取得实效。"+extra;
            default -> "下一步，将围绕既定目标细化措施、压实责任并跟踪实施成效。"+extra;
        };
    }
    private String formalize(String text,String instruction){
        String value=text.replace("马上","及时").replace("搞好","做好").replace("做出贡献","作出贡献").replace("截止目前","截至目前");
        if(StringUtils.hasText(instruction)&&instruction.contains("逻辑")&&!value.startsWith("为此")) value="为此，"+value;
        return value;
    }
    private String compress(String text,int target){
        if(text.codePointCount(0,text.length())<=target)return text;
        int end=text.offsetByCodePoints(0,target);String value=text.substring(0,end).replaceAll("[，、；：]$","");
        return value.matches(".*[。！？]$")?value:value+"。";
    }
    private String expand(String text,WritingTaskEntity task,String instruction,Integer target){
        String addition="同时，要坚持依法依规、实事求是，进一步明确责任主体、工作标准和完成时限，强化过程监督与风险防控。";
        if(StringUtils.hasText(instruction))addition+="具体按照“"+instruction.trim()+"”要求抓好落实。";
        String value=text+(text.matches(".*[。！？]$")?"":"。")+addition;
        int desired=target==null?value.codePointCount(0,value.length()):target;
        while(value.codePointCount(0,value.length())<desired)value+="围绕"+task.getWritingTopic()+"，及时总结实施情况并动态完善相关措施。";
        return value;
    }
}
