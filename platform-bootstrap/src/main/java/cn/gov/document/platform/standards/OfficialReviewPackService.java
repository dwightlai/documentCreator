package cn.gov.document.platform.standards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned, attributable reference data. Installation never overwrites tenant edits. */
@Service
public class OfficialReviewPackService {
    private final JdbcTemplate jdbc;
    private final JsonNode pack;
    private final JsonNode variantGroups;

    public OfficialReviewPackService(JdbcTemplate jdbc, ObjectMapper mapper) throws IOException {
        this.jdbc = jdbc;
        try (var input = new ClassPathResource("standards/official-review-pack.json").getInputStream()) {
            this.pack = mapper.readTree(input);
        }
        try (var input = new ClassPathResource("standards/gf1001-2001-variants.json").getInputStream()) {
            this.variantGroups = mapper.readTree(input).path("groups");
        }
        validatePack();
    }

    public PackInfo info(String tenantId) {
        boolean installed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM official_review_pack_install WHERE tenant_id=? AND pack_code=? AND pack_version=?",
                Integer.class, tenantId, code(), version()) > 0;
        return new PackInfo(code(), version(), pack.path("title").asText(), pack.path("scope").asText(),
                pack.path("rules").size(), automaticRuleCount(), termCount(), installed);
    }

    @Transactional
    public InstallResult install(String tenantId, String actor) {
        if (info(tenantId).installed()) return new InstallResult(false, 0, 0, 0);
        int dictionaries = 0, terms = 0, rules = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (JsonNode definition : pack.path("dictionaries")) {
            if (exists("ai_review_dictionary", "dict_code", tenantId, definition.path("code").asText())) continue;
            String id = id();
            String basis = basis(definition);
            List<TermData> entries = terms(definition);
            jdbc.update("INSERT INTO ai_review_dictionary (id,tenant_id,dict_code,dict_name,dict_type,source_description,current_version,entry_count,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,1,?,?,?,?,?,FALSE,0)",
                    id, tenantId, definition.path("code").asText(), definition.path("name").asText(),
                    definition.path("type").asText(), basis, entries.size(),
                    now, now, actor, actor);
            dictionaries++;
            for (TermData term : entries) {
                jdbc.update("INSERT INTO ai_review_term (id,tenant_id,dictionary_id,term_text,standard_form,aliases,category_tag,risk_level,source_reference,usage_note,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,0)",
                        id(), tenantId, id, term.text(), term.standard(), "[]", definition.path("type").asText(), "提示", basis,
                        term.note(), now, now, actor, actor);
                terms++;
            }
        }
        for (JsonNode rule : pack.path("rules")) {
            if (exists("ai_review_rule", "rule_code", tenantId, rule.path("code").asText())) continue;
            String id = id();
            String expression = rule.path("expression").asText();
            jdbc.update("INSERT INTO ai_review_rule (id,tenant_id,rule_code,rule_name,category,applicable_document_types,risk_level,detection_method,rule_expression,basis,lifecycle_status,current_version,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,1,TRUE,?,?,?,?,FALSE,0)",
                    id, tenantId, rule.path("code").asText(), rule.path("name").asText(),
                    rule.path("category").asText(), rule.path("types").toString(),
                    rule.path("risk").asText(), rule.path("method").asText(), expression, basis(rule),
                    "生效", now, now, actor, actor);
            jdbc.update("INSERT INTO ai_review_rule_version (id,tenant_id,rule_id,version_number,rule_expression,change_description,approved_by,approved_at,test_results,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,1,?,?,?,?,?,?,?,?,?,FALSE,0)",
                    id(), tenantId, id, expression, "官方规范数据包 " + version(), actor, now,
                    rule.path("method").asText().equals("人工核验") ? "人工核验条款，不执行文本匹配" : "已校验表达式语法",
                    now, now, actor, actor);
            rules++;
        }
        jdbc.update("INSERT INTO official_review_pack_install (tenant_id,pack_code,pack_version,installed_at,installed_by) VALUES (?,?,?,?,?)",
                tenantId, code(), version(), now, actor);
        return new InstallResult(true, dictionaries, terms, rules);
    }

    private boolean exists(String table, String codeColumn, String tenantId, String code) {
        // Table and column names are fixed internal constants, never request values.
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND " + codeColumn + "=? AND is_deleted=FALSE",
                Integer.class, tenantId, code) > 0;
    }

    private String basis(JsonNode entry) {
        return pack.path("sources").path(entry.path("source").asText()).asText() + "；" + entry.path("clause").asText();
    }

    private void validatePack() {
        if (code().isBlank() || version().isBlank() || !pack.path("rules").isArray() || !pack.path("dictionaries").isArray())
            throw new IllegalStateException("官方规范数据包结构无效");
        if (!variantGroups.isArray() || variantGroups.size() != 338)
            throw new IllegalStateException("GF 1001—2001 词形数据不完整");
        List<String> codes = new ArrayList<>();
        for (JsonNode dictionary : pack.path("dictionaries")) {
            if (!codes.add(dictionary.path("code").asText()) || !pack.path("sources").has(dictionary.path("source").asText()))
                throw new IllegalStateException("官方词库重复或缺少来源：" + dictionary.path("code").asText());
            terms(dictionary);
        }
        List<String> dictionaryCodes = List.copyOf(codes);
        codes.clear();
        List<String> categories = List.of("政治与重要表述", "政策法规与事实依据", "公文业务规范", "内容与逻辑", "语言文字", "格式与版式");
        for (JsonNode entry : pack.path("rules")) {
            String code = entry.path("code").asText();
            if (!codes.add(code) || !pack.path("sources").has(entry.path("source").asText()))
                throw new IllegalStateException("官方审校规则重复或缺少来源：" + code);
            if (!categories.contains(entry.path("category").asText()))
                throw new IllegalStateException("官方审校规则分类无效：" + code);
            if (!List.of("词项", "正则", "词库").contains(entry.path("method").asText())
                    || entry.path("expression").asText().isBlank())
                throw new IllegalStateException("官方审校规则不可执行：" + code);
            if ("词库".equals(entry.path("method").asText()) && !dictionaryCodes.contains(entry.path("expression").asText()))
                throw new IllegalStateException("官方审校规则引用的词库不存在：" + code);
            if ("正则".equals(entry.path("method").asText())) {
                String expression = entry.path("expression").asText();
                Pattern.compile(expression.split("=>", 2)[0]);
            }
        }
        for (String dictionaryCode : dictionaryCodes) {
            boolean referenced = false;
            for (JsonNode rule : pack.path("rules")) {
                if ("词库".equals(rule.path("method").asText())
                        && dictionaryCode.equals(rule.path("expression").asText())) {
                    referenced = true;
                    break;
                }
            }
            if (!referenced) throw new IllegalStateException("官方词库未被审校规则使用：" + dictionaryCode);
        }
    }

    private String code() { return pack.path("code").asText(); }
    private String version() { return pack.path("version").asText(); }
    private int termCount() {
        int count = 0;
        for (JsonNode dictionary : pack.path("dictionaries")) count += terms(dictionary).size();
        return count;
    }
    private List<TermData> terms(JsonNode dictionary) {
        List<TermData> entries = new ArrayList<>();
        if (dictionary.has("dataResource")) {
            if (!"standards/gf1001-2001-variants.json".equals(dictionary.path("dataResource").asText()))
                throw new IllegalStateException("未知的官方词形资源");
            for (JsonNode group : variantGroups) {
                String preferred = group.path("preferred").asText();
                if (preferred.isBlank() || !group.path("variants").isArray() || group.path("variants").isEmpty())
                    throw new IllegalStateException("GF 1001—2001 词形条目无效");
                for (JsonNode variant : group.path("variants")) {
                    String text = variant.asText();
                    if (text.isBlank() || preferred.equals(text))
                        throw new IllegalStateException("GF 1001—2001 词形条目无效");
                    entries.add(new TermData(text, preferred, "试行规范推荐词形；仅在同义语境下提示核对，不应直接自动替换。"));
                }
            }
        } else {
            if (!dictionary.path("terms").isArray()) throw new IllegalStateException("官方词库缺少词条");
            for (JsonNode term : dictionary.path("terms")) {
                String text = term.asText();
                if (text.isBlank()) throw new IllegalStateException("官方词库含空词条");
                entries.add(new TermData(text, text, "规范项目；用于查阅，不将正确写法报为错误。"));
            }
        }
        return entries;
    }
    private int automaticRuleCount() {
        int count = 0;
        for (JsonNode rule : pack.path("rules")) if (!"人工核验".equals(rule.path("method").asText())) count++;
        return count;
    }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }

    public record PackInfo(String code, String version, String title, String scope, int ruleCount, int automaticRuleCount, int termCount, boolean installed) {}
    public record InstallResult(boolean installed, int dictionaries, int terms, int rules) {}
    private record TermData(String text, String standard, String note) {}
}
