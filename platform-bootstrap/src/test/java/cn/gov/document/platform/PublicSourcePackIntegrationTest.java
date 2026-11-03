package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.knowledge.application.KnowledgeService;
import cn.gov.document.platform.publicdata.PublicSourcePackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PublicSourcePackIntegrationTest {
    @Autowired PublicSourcePackService pack;
    @Autowired KnowledgeService knowledge;
    @Autowired JdbcTemplate jdbc;

    @Test
    void installsFiveAttributedCategoriesAndKeepsExistingTenantEdits() {
        String tenant = "tenant-public-pack-test";
        var info = pack.info(tenant);
        assertThat(info.categories()).isEqualTo(5);
        assertThat(info.entries()).isEqualTo(50);
        var first = pack.install(tenant, "pack-admin");
        assertThat(first.installed()).isTrue();
        assertThat(first.sources()).isEqualTo(5);
        assertThat(first.documents()).isEqualTo(50);
        assertThat(pack.install(tenant, "pack-admin").installed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_knowledge_source WHERE tenant_id=? AND is_deleted=FALSE", Integer.class, tenant)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_knowledge_document WHERE tenant_id=? AND is_deleted=FALSE", Integer.class, tenant)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_knowledge_document WHERE tenant_id=? AND document_title LIKE '离线原文%'", Integer.class, tenant)).isEqualTo(8);
        RequestContext.set("pack-test", tenant, "pack-admin");
        try {
            var hits = knowledge.search("请示", null, 20, "keyword");
            assertThat(hits).isNotEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_knowledge_document WHERE tenant_id=? AND metadata LIKE '%sourceUrl%'", Integer.class, tenant)).isEqualTo(50);
            assertThat(knowledge.search("国家金融监督管理总局", null, 10, "keyword")).isNotEmpty();
        } finally {
            RequestContext.clear();
        }
    }
}
