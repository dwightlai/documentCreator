package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.rule.application.EffectiveReviewRuleProvider;
import cn.gov.document.platform.standards.OfficialReviewPackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OfficialReviewPackIntegrationTest {
    @Autowired
    OfficialReviewPackService service;

    @Autowired
    EffectiveReviewRuleProvider provider;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void installsExecutablePackOnceWithoutFlaggingCorrectVocabulary() throws IOException {
        var pack = mapper.readTree(new ClassPathResource("standards/official-review-pack.json").getInputStream());
        int ruleCount = pack.path("rules").size();
        int dictionaryCount = pack.path("dictionaries").size();
        int termCount = 0;
        for (var dictionary : pack.path("dictionaries")) {
            var groups = mapper.readTree(new ClassPathResource(
                                    dictionary.path("dataResource").asText())
                            .getInputStream())
                    .path("groups");
            for (var group : groups) termCount += group.path("variants").size();
        }
        String tenant = "tenant-official-pack-test";
        var info = service.info(tenant);
        assertThat(info.ruleCount()).isEqualTo(ruleCount);
        assertThat(info.automaticRuleCount()).isEqualTo(ruleCount);
        assertThat(info.termCount()).isEqualTo(termCount);
        var installed = service.install(tenant, "review-admin");
        assertThat(installed.installed()).isTrue();
        assertThat(installed.dictionaries()).isEqualTo(dictionaryCount);
        assertThat(installed.terms()).isEqualTo(termCount);
        assertThat(installed.rules()).isEqualTo(ruleCount);
        assertThat(service.install(tenant, "review-admin").installed()).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_review_rule WHERE tenant_id=? AND is_deleted=FALSE",
                        Integer.class,
                        tenant))
                .isEqualTo(ruleCount);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_review_term WHERE tenant_id=? AND is_deleted=FALSE",
                        Integer.class,
                        tenant))
                .isEqualTo(termCount);
        RequestContext.set("pack-test", tenant, "review-admin");
        try {
            assertThat(provider.inspect("请示", "请示、标题、正文和成文日期均已核对。")).isEmpty();
            assertThat(provider.inspect("通知", "某办发（2026）01号")).isNotEmpty();
            assertThat(provider.inspect("通知", "请相关单位参预会议。")).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("参预");
                assertThat(match.replacement()).isEqualTo("参与");
            });
            assertThat(provider.inspect("通知", "成文日期：2026年01月09日。"))
                    .extracting(EffectiveReviewRuleProvider.Match::sourceCode)
                    .anyMatch(code -> code.startsWith("STD-DATE-MONTH-LEADING-ZERO"))
                    .anyMatch(code -> code.startsWith("STD-DATE-DAY-LEADING-ZERO"));
            assertThat(provider.inspect("通知", "请办理，，并反馈。。"))
                    .extracting(EffectiveReviewRuleProvider.Match::sourceCode)
                    .anyMatch(code -> code.startsWith("STD-PUNCT-REPEATED-COMMA"))
                    .anyMatch(code -> code.startsWith("STD-PUNCT-REPEATED-PERIOD"));
            assertThat(provider.inspect("通知", "请办理,并反馈:完成。"))
                    .anySatisfy(match -> {
                        assertThat(match.original()).isEqualTo(",");
                        assertThat(match.replacement()).isEqualTo("，");
                    })
                    .anySatisfy(match -> {
                        assertThat(match.original()).isEqualTo(":");
                        assertThat(match.replacement()).isEqualTo("：");
                    });
            assertThat(provider.inspect("通知", "甲,乙,丙").stream()
                            .filter(match -> match.sourceCode().startsWith("STD-PUNCT-ASCII-COMMA-BETWEEN-HAN")))
                    .hasSize(2)
                    .allSatisfy(match -> assertThat(match.replacement()).isEqualTo("，"));
            assertThat(provider.inspect("通知", "有关事项……等，请核对。")).anySatisfy(match -> {
                assertThat(match.sourceCode()).startsWith("STD-PUNCT-ELLIPSIS-REDUNDANT-WORD");
                assertThat(match.replacement()).isNull();
            });
            assertThat(provider.inspect("通知", "…… …… ……"))
                    .noneMatch(match -> match.sourceCode().startsWith("STD-PUNCT-ELLIPSIS-OVERLONG"));
            assertThat(provider.inspect("通知", "……………………"))
                    .anyMatch(match -> match.sourceCode().startsWith("STD-PUNCT-ELLIPSIS-OVERLONG"));
            assertThat(provider.inspect("通知", "请将工作布署到位。")).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("布署到位");
                assertThat(match.replacement()).isEqualTo("部署到位");
            });
            assertThat(provider.inspect("通知", "部署工作已经完成。")).isEmpty();
            assertThat(provider.inspect("通知", "要再接再励。")).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("再接再励");
                assertThat(match.replacement()).isEqualTo("再接再厉");
            });
            assertThat(provider.inspect("通知", "面积按平方公尺计算。"))
                    .anySatisfy(match -> {
                        assertThat(match.original()).isEqualTo("平方公尺");
                        assertThat(match.replacement()).isEqualTo("平方米");
                    })
                    .noneMatch(match -> "公尺".equals(match.original()));
            assertThat(provider.inspect("通知", "国家发展与改革委员会")).anySatisfy(match -> {
                assertThat(match.original()).isEqualTo("国家发展与改革委员会");
                assertThat(match.replacement()).isEqualTo("国家发展和改革委员会");
            });
        } finally {
            RequestContext.clear();
        }
    }

    @Test
    void replaysEveryPublishedVariantAndEveryRuleBoundary() throws IOException {
        String tenant = "tenant-official-pack-regression";
        service.install(tenant, "review-admin");
        RequestContext.set("pack-regression", tenant, "review-admin");
        try {
            var pack = mapper.readTree(new ClassPathResource("standards/official-review-pack.json").getInputStream());
            int regexRules = 0;
            for (var rule : pack.path("rules"))
                if ("正则".equals(rule.path("method").asText())) regexRules++;
            for (var dictionary : pack.path("dictionaries")) {
                String ruleCode = null;
                for (var rule : pack.path("rules")) {
                    if ("词库".equals(rule.path("method").asText())
                            && dictionary
                                    .path("code")
                                    .asText()
                                    .equals(rule.path("expression").asText()))
                        ruleCode = rule.path("code").asText();
                }
                var groups = mapper.readTree(new ClassPathResource(
                                        dictionary.path("dataResource").asText())
                                .getInputStream())
                        .path("groups");
                StringBuilder variants = new StringBuilder();
                StringBuilder preferred = new StringBuilder();
                Set<String> allVariants = new HashSet<>();
                for (var group : groups) {
                    preferred.append(group.path("preferred").asText()).append('。');
                    for (var variant : group.path("variants")) {
                        variants.append(variant.asText()).append('。');
                        allVariants.add(variant.asText());
                    }
                }
                String prefix = ruleCode + "@";
                assertThat(provider.inspect("通知", variants.toString()).stream()
                                .filter(match -> match.sourceCode().startsWith(prefix))
                                .map(EffectiveReviewRuleProvider.Match::original)
                                .collect(java.util.stream.Collectors.toSet()))
                        .containsAll(allVariants);
                assertThat(provider.inspect("通知", preferred.toString()))
                        .noneMatch(match -> match.sourceCode().startsWith(prefix));
            }

            var cases = mapper.readTree(new ClassPathResource("standards/review-rule-cases.json").getInputStream());
            assertThat(cases).hasSize(regexRules);
            for (var example : cases) {
                String code = example.path("rule").asText();
                var positive = provider.inspect("通知", example.path("positive").asText()).stream()
                        .filter(match -> match.sourceCode().startsWith(code + "@"))
                        .toList();
                assertThat(positive)
                        .as(code + " should report the positive case")
                        .isNotEmpty();
                if (example.has("replacement"))
                    assertThat(positive)
                            .extracting(EffectiveReviewRuleProvider.Match::replacement)
                            .contains(example.path("replacement").asText());
                else
                    assertThat(positive)
                            .allSatisfy(match -> assertThat(match.replacement()).isNull());
                assertThat(provider.inspect("通知", example.path("negative").asText()))
                        .as(code + " should ignore the negative case")
                        .noneMatch(match -> match.sourceCode().startsWith(code + "@"));
            }
        } finally {
            RequestContext.clear();
        }
    }
}
