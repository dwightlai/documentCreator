package cn.gov.document.platform.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewCategoryTest {
    @Test
    void keepsBusinessCategorySeparateFromExecutionCapability() {
        assertThat(ReviewCategory.of("保密要求")).isEqualTo(ReviewCategory.DOCUMENT);
        assertThat(ReviewCategory.of("内容安全")).isEqualTo(ReviewCategory.DOCUMENT);
        assertThat(ReviewCategory.of("政治表述")).isEqualTo(ReviewCategory.POLITICAL);
        assertThat(ReviewCategory.levels(List.of(ReviewCategory.DOCUMENT)))
                .contains("L3", "L7")
                .doesNotContain("L1", "L5");
    }
}
