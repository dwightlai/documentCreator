package cn.gov.document.platform.review.application.deep;

import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import java.math.BigDecimal;

/**
 * 深度审校（L5—L7）发现的问题。深度问题一律不提供替换文本：数字、日期、逻辑与内容安全问题只给出判断依据和修改方向，由人工处理（§21）。
 */
public record Finding(
        String level,
        DocumentParagraphEntity paragraph,
        String category,
        String risk,
        int start,
        int end,
        String original,
        String explanation,
        String basis,
        BigDecimal confidence) {}
