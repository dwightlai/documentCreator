package cn.gov.document.platform.writing.application;

import cn.gov.document.platform.writing.api.DocumentTypeConfigDtos;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only platform defaults. A tenant can override a default by creating a
 * configuration with the same type code; the tenant version always wins.
 */
final class BuiltInDocumentTypes {
    private static final OffsetDateTime BASELINE_TIME = OffsetDateTime.parse("2026-07-15T00:00:00+08:00");
    private static final List<String> COMMON_REQUIRED = List.of("标题", "正文", "落款");
    private static final Map<String, Definition> DEFINITIONS = definitions();

    private BuiltInDocumentTypes() { }

    static List<DocumentTypeConfigDtos.Response> list() {
        return DEFINITIONS.values().stream().map(BuiltInDocumentTypes::response).toList();
    }

    static Optional<DocumentTypeConfigDtos.Response> find(String code) {
        return Optional.ofNullable(DEFINITIONS.get(code)).map(BuiltInDocumentTypes::response);
    }

    static Optional<DocumentTypeConfigService.RuntimeConfig> effective(String code) {
        Definition definition = DEFINITIONS.get(code);
        return definition == null ? Optional.empty() : Optional.of(new DocumentTypeConfigService.RuntimeConfig(
                definition.required(), definition.structure(), definition.forbidden(), definition.closing()));
    }

    private static DocumentTypeConfigDtos.Response response(Definition definition) {
        return new DocumentTypeConfigDtos.Response("builtin-" + definition.code(), definition.code(), definition.code(),
                definition.category(), definition.scenario(), definition.direction(), definition.required(), definition.structure(),
                definition.forbidden(), definition.closing(), 1, "生效", 0, BASELINE_TIME);
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> values = new LinkedHashMap<>();
        add(values, "通知", "法定公文", "下行", "发布、传达要求周知或执行的事项", List.of("通知缘由", "通知事项", "执行要求"), List.of("事项不明确", "责任主体缺失"), List.of("特此通知。"));
        add(values, "通报", "法定公文", "下行", "表彰先进、批评错误或传达重要情况", List.of("基本事实", "性质与影响", "处理决定", "工作要求"), List.of("事实依据不明", "评价失当"), List.of("特此通报。"));
        add(values, "报告", "法定公文", "上行", "向上级机关汇报工作、反映情况或回复询问", List.of("总体情况", "主要工作", "问题分析", "下一步安排"), List.of("夹带请示事项", "提出需批复请求"), List.of("特此报告。"));
        add(values, "请示", "法定公文", "上行", "向上级机关请求指示、批准", List.of("请示背景与依据", "具体事项", "请求内容", "结束语"), List.of("一文多事", "同时抄送下级机关", "使用命令式语气"), List.of("妥否，请批示。", "以上请示，请予批复。"));
        add(values, "批复", "法定公文", "下行", "答复下级机关请示事项", List.of("请示引用", "批复意见", "执行要求"), List.of("未对应原请示", "新增无关事项"), List.of("此复。"));
        add(values, "函", "法定公文", "平行", "不相隶属机关之间商洽、询问或答复事项", List.of("行函缘由", "商洽或答复事项", "期望安排"), List.of("使用命令式语气", "隶属关系错误"), List.of("特此函达。", "盼复。"));
        add(values, "纪要", "法定公文", "内部", "记载会议主要情况和议定事项", List.of("会议概况", "议定事项", "责任分工", "落实时限"), List.of("逐字会议记录", "未形成责任闭环"), List.of());
        add(values, "意见", "法定公文", "下行", "对重要问题提出见解和处理办法", List.of("总体要求", "主要任务", "保障措施"), List.of("目标与措施脱节", "职责不清"), List.of());
        add(values, "决定", "法定公文", "下行", "对重要事项作出决策和部署", List.of("决定依据", "决定事项", "执行要求"), List.of("权限依据不足", "决定事项模糊"), List.of("本决定自发布之日起施行。"));
        add(values, "公告", "法定公文", "下行", "向国内外宣布重要事项或法定事项", List.of("公告依据", "公告事项", "生效安排"), List.of("公开范围不当", "法定依据缺失"), List.of("特此公告。"));
        add(values, "通告", "法定公文", "下行", "在一定范围公布应当遵守或周知的事项", List.of("通告依据", "具体事项", "实施时间", "责任要求"), List.of("适用范围不明", "执行主体缺失"), List.of("特此通告。"));

        add(values, "工作总结", "事务文书", "内部", "阶段性回顾工作成效、问题与经验", List.of("总体概况", "主要成效", "经验做法", "问题不足", "下一步打算"), List.of("只有成绩没有问题", "数据无来源"), List.of());
        add(values, "工作计划", "事务文书", "内部", "明确阶段目标、任务、分工与进度", List.of("总体目标", "重点任务", "进度安排", "责任分工", "保障措施"), List.of("目标不可衡量", "任务无责任人"), List.of());
        add(values, "领导讲话稿", "事务文书", "内部", "用于会议、活动的领导讲话", List.of("开场与形势判断", "成绩与问题", "重点部署", "工作要求"), List.of("身份口吻不符", "空泛口号堆砌"), List.of());
        add(values, "汇报材料", "事务文书", "上行", "向上级或会议汇报工作进展", List.of("基本情况", "工作进展", "存在问题", "下一步安排", "需协调事项"), List.of("事实与观点混淆", "问题无对策"), List.of());
        add(values, "调研报告", "事务文书", "内部", "呈现调查过程、发现、分析和建议", List.of("调研背景", "调研方法", "现状与发现", "问题成因", "对策建议"), List.of("样本来源缺失", "结论无证据"), List.of());
        add(values, "情况说明", "事务文书", "内部", "客观说明事件、原因和处理情况", List.of("事项概述", "事实经过", "原因分析", "处置情况", "后续安排"), List.of("主观推断冒充事实", "关键时间缺失"), List.of("特此说明。"));
        add(values, "工作方案", "事务文书", "内部", "组织实施专项工作或活动", List.of("工作目标", "组织领导", "重点任务", "步骤安排", "责任分工", "保障措施"), List.of("任务无时限", "职责交叉冲突"), List.of());
        add(values, "会议材料", "事务文书", "内部", "支撑会议议程、讨论或决策", List.of("会议背景", "核心议题", "相关情况", "讨论建议"), List.of("议题不聚焦", "决策信息不足"), List.of());
        add(values, "简报", "事务文书", "内部", "快速反映工作动态、经验和问题", List.of("标题导语", "主要情况", "典型做法", "工作启示"), List.of("篇幅冗长", "信息要素缺失"), List.of());
        add(values, "信息报送", "事务文书", "上行", "及时向上级报送重要动态和信息", List.of("核心事实", "进展影响", "处置措施", "趋势研判"), List.of("时效信息缺失", "未经核实结论"), List.of());
        add(values, "制度草案", "事务文书", "内部", "起草内部制度、办法或细则", List.of("总则", "职责分工", "工作程序", "监督管理", "附则"), List.of("上位依据冲突", "权责不对等"), List.of("本制度自发布之日起施行。"));
        add(values, "新闻稿", "事务文书", "内部", "对外发布政务活动和工作成效信息", List.of("标题", "导语", "主体事实", "背景信息", "结尾"), List.of("夸大宣传", "未授权敏感信息", "新闻要素不全"), List.of());
        return Map.copyOf(values);
    }

    private static void add(Map<String, Definition> target, String code, String category, String direction, String scenario,
                            List<String> structure, List<String> forbidden, List<String> closing) {
        target.put(code, new Definition(code, category, direction, scenario, COMMON_REQUIRED, structure, forbidden, closing));
    }

    private record Definition(String code, String category, String direction, String scenario, List<String> required,
                              List<String> structure, List<String> forbidden, List<String> closing) { }
}
