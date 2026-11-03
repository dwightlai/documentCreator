package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.rule.application.EffectiveReviewRuleProvider;
import cn.gov.document.platform.standards.OfficialReviewPackService;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OfficialReviewPackIntegrationTest {
    @Autowired OfficialReviewPackService service;
    @Autowired EffectiveReviewRuleProvider provider;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void installsExecutablePackOnceWithoutFlaggingCorrectVocabulary() {
        String tenant = "tenant-official-pack-test";
        var info = service.info(tenant);
        assertThat(info.ruleCount()).isEqualTo(9);
        assertThat(info.automaticRuleCount()).isEqualTo(9);
        assertThat(info.termCount()).isEqualTo(362);
        var installed = service.install(tenant, "review-admin");
        assertThat(installed.installed()).isTrue();
        assertThat(installed.dictionaries()).isEqualTo(1);
        assertThat(installed.terms()).isEqualTo(362);
        assertThat(installed.rules()).isEqualTo(9);
        assertThat(service.install(tenant, "review-admin").installed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_rule WHERE tenant_id=? AND is_deleted=FALSE", Integer.class, tenant)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_term WHERE tenant_id=? AND is_deleted=FALSE", Integer.class, tenant)).isEqualTo(362);
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
        } finally {
            RequestContext.clear();
        }
    }

    @Test
    void removesOnlyUntouchedReferenceEntriesFromAnOlderInstalledPack() throws Exception {
        String tenant = "tenant-official-pack-retirement-test";
        OffsetDateTime created = OffsetDateTime.now();
        jdbc.update("INSERT INTO official_review_pack_install (tenant_id,pack_code,pack_version,installed_at,installed_by) VALUES (?,?,?,?,?)",
                tenant, "CN-OFFICIAL-DOCUMENT-BASE", "2026.11.03-4", created, "official-pack");
        jdbc.update("INSERT INTO ai_review_dictionary (id,tenant_id,dict_code,dict_name,dict_type,source_description,current_version,entry_count,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,1,1,?,?,?,?,FALSE,0)",
                "old-pack-dict", tenant, "NAT-DOCUMENT-TYPES-2012", "法定文种", "标准文种", "公开规范", created, created, "official-pack", "official-pack");
        jdbc.update("INSERT INTO ai_review_term (id,tenant_id,dictionary_id,term_text,standard_form,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,?,?,?,FALSE,0)",
                "old-pack-term", tenant, "old-pack-dict", "通知", "通知", created, created, "official-pack", "official-pack");
        jdbc.update("INSERT INTO ai_review_rule (id,tenant_id,rule_code,rule_name,category,risk_level,detection_method,rule_expression,lifecycle_status,current_version,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,?,?,?,1,TRUE,?,?,?,?,FALSE,0)",
                "old-pack-rule", tenant, "STD-DOC-TYPE", "核验文种", "公文业务规范", "一般", "人工核验", "人工核验", "生效", created, created, "official-pack", "official-pack");
        jdbc.update("INSERT INTO ai_review_rule_version (id,tenant_id,rule_id,version_number,change_description,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,1,?,?,?,?,?,FALSE,0)",
                "old-pack-version", tenant, "old-pack-rule", "官方规范数据包 2026.11.03-4", created, created, "official-pack", "official-pack");
        jdbc.update("INSERT INTO ai_review_rule (id,tenant_id,rule_code,rule_name,category,risk_level,detection_method,rule_expression,lifecycle_status,current_version,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,?,?,?,1,TRUE,?,?,?,?,FALSE,0)",
                "user-manual-rule", tenant, "USER-MANUAL", "用户核验规则", "公文业务规范", "一般", "人工核验", "人工核验", "生效", created, created, "review-admin", "review-admin");

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/common/V45__remove_reference_only_review_pack_data.sql"));
        }

        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ai_review_rule WHERE id='old-pack-rule'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ai_review_rule WHERE id='user-manual-rule'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ai_review_dictionary WHERE id='old-pack-dict'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ai_review_term WHERE id='old-pack-term'", Boolean.class)).isTrue();
    }
}
