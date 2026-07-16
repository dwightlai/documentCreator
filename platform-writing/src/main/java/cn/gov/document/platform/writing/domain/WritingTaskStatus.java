package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.error.BusinessException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum WritingTaskStatus {
    DRAFT("草稿"),
    MATERIAL_PREPARING("材料准备中"),
    OUTLINE_PENDING("大纲待确认"),
    GENERATING("正文生成中"),
    EDITING("编辑中"),
    REVIEW_PENDING("待审校"),
    REVIEWING("审校处理中"),
    SUBMIT_PENDING("待提交"),
    SUBMITTED("已提交OA"),
    CLOSED("已关闭");

    private final String code;

    WritingTaskStatus(String code) { this.code = code; }
    public String code() { return code; }

    public static WritingTaskStatus fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
            .orElseThrow(() -> BusinessException.unprocessable("未知写作任务状态: " + code));
    }

    public boolean canTransitionTo(WritingTaskStatus target) {
        if (target == CLOSED) return this != CLOSED;
        Set<WritingTaskStatus> allowed = switch (this) {
            case DRAFT -> EnumSet.of(MATERIAL_PREPARING);
            case MATERIAL_PREPARING -> EnumSet.of(OUTLINE_PENDING);
            case OUTLINE_PENDING -> EnumSet.of(GENERATING);
            case GENERATING -> EnumSet.of(EDITING);
            case EDITING -> EnumSet.of(REVIEW_PENDING);
            case REVIEW_PENDING -> EnumSet.of(REVIEWING, EDITING);
            case REVIEWING -> EnumSet.of(SUBMIT_PENDING, EDITING);
            case SUBMIT_PENDING -> EnumSet.of(SUBMITTED, EDITING);
            case SUBMITTED, CLOSED -> EnumSet.noneOf(WritingTaskStatus.class);
        };
        return allowed.contains(target);
    }
}
