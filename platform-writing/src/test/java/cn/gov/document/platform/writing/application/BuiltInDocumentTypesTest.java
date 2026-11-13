package cn.gov.document.platform.writing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BuiltInDocumentTypesTest {
    @Test
    void includesAllFifteenOfficialDocumentTypes() {
        // 《党政机关公文处理工作条例》第八条的完整文种集合。
        Set<String> statutory =
                Set.of("决议", "决定", "命令", "公报", "公告", "通告", "意见", "通知", "通报", "报告", "请示", "批复", "议案", "函", "纪要");
        assertThat(BuiltInDocumentTypes.list().stream()
                        .filter(type -> "法定公文".equals(type.documentCategory()))
                        .map(type -> type.typeCode())
                        .collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(statutory);
    }
}
