package cn.gov.document.platform.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.writing.domain.WritingOutlineNodeEntity;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import org.junit.jupiter.api.Test;

class RuleBasedDocumentGeneratorTest {
    private final RuleBasedDocumentGenerator generator = new RuleBasedDocumentGenerator();

    @Test
    void reportSectionsUseDifferentPromptsWithoutInventingFacts() {
        WritingTaskEntity task = new WritingTaskEntity();
        task.setDocumentTypeCode("报告");
        task.setWritingTopic("未来五年发展计划");
        String overview = generate(task, "总体情况");
        String work = generate(task, "主要工作");
        String problems = generate(task, "问题分析");
        String next = generate(task, "下一步安排");

        assertThat(overview).contains("工作背景").doesNotContain("已取得");
        assertThat(work).contains("重点工作");
        assertThat(problems).contains("主要问题");
        assertThat(next).contains("责任分工");
        assertThat(overview).isNotEqualTo(work).isNotEqualTo(problems).isNotEqualTo(next);
    }

    private String generate(WritingTaskEntity task, String title) {
        WritingOutlineNodeEntity node = new WritingOutlineNodeEntity();
        node.setNodeTitle(title);
        return generator.generate(task, node, null);
    }
}
