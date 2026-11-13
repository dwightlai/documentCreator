package cn.gov.document.platform.datadict;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-dicts")
public class DataDictController {
    private static final Map<String, SeedType> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("document_direction", seed("document_direction", "行文方向", 10, "上行", "下行", "平行", "内部"));
        DEFAULTS.put("security_level", seed("security_level", "密级/数据权限", 20, "公开", "内部", "秘密", "机密"));
        DEFAULTS.put("material_authority", seed("material_authority", "材料权威级别", 30, "正式公文", "权威资料", "内部资料", "参考资料"));
        DEFAULTS.put("material_usage_mode", seed("material_usage_mode", "材料使用方式", 40, "全文参考", "指定片段", "仅作背景"));
        DEFAULTS.put(
                "material_citation_purpose",
                seed("material_citation_purpose", "材料引用目的", 50, "事实依据", "政策依据", "案例参考", "表达参考"));
        DEFAULTS.put("knowledge_usage_type", seed("knowledge_usage_type", "知识写作用途", 60, "待分类", "规范依据", "事实依据", "写法参考"));
        DEFAULTS.put("knowledge_completeness", seed("knowledge_completeness", "入库完整性", 70, "本地原文可用", "原文摘录", "来源索引"));
        DEFAULTS.put(
                "knowledge_verification",
                seed("knowledge_verification", "内容核验状态", 80, "待整理", "待审核", "待核验", "已核验", "退回"));
        DEFAULTS.put("document_category", seed("document_category", "文种分类", 90, "法定公文", "事务文书"));
        DEFAULTS.put("model_data_level", seed("model_data_level", "模型数据级别", 100, "普通", "内部", "敏感"));
        DEFAULTS.put("model_scene", seed("model_scene", "模型调用场景", 110, "段落生成", "文本改写"));
    }

    private final JdbcTemplate jdbc;

    public DataDictController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TypeInput(
            @NotBlank String typeCode,
            @NotBlank String typeName,
            String description,
            Integer sortOrder,
            Boolean enabled) {}

    public record ItemInput(
            @NotBlank String itemCode, @NotBlank String itemLabel, Integer sortOrder, Boolean enabled) {}

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listTypes() {
        ensureDefaults();
        return ApiResponse.success(jdbc.queryForList(
                """
                SELECT id, type_code, type_name, description, sort_order, enabled, lock_version
                FROM ai_data_dict_type
                WHERE tenant_id=? AND is_deleted=FALSE
                ORDER BY sort_order, type_code
                """,
                tenant()));
    }

    @GetMapping("/{typeCode}/items")
    public ApiResponse<List<Map<String, Object>>> listItems(
            @PathVariable String typeCode, @RequestParam(required = false, defaultValue = "true") boolean enabledOnly) {
        ensureDefaults();
        requireType(typeCode);
        String sql =
                """
                SELECT id, type_code, item_code, item_label, sort_order, enabled, lock_version
                FROM ai_data_dict_item
                WHERE tenant_id=? AND type_code=? AND is_deleted=FALSE
                """
                        + (enabledOnly ? " AND enabled=TRUE" : "")
                        + " ORDER BY sort_order, item_code";
        return ApiResponse.success(jdbc.queryForList(sql, tenant(), typeCode.trim()));
    }

    @PostMapping
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Map<String, Object>> createType(@Valid @RequestBody TypeInput input) {
        ensureDefaults();
        String code = input.typeCode().trim();
        if (existsType(code, null)) throw BusinessException.conflict("字典类型编码已存在");
        String id = id();
        jdbc.update(
                """
                INSERT INTO ai_data_dict_type
                (id,tenant_id,type_code,type_name,description,sort_order,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,FALSE,0)
                """,
                id,
                tenant(),
                code,
                input.typeName().trim(),
                blank(input.description()),
                input.sortOrder() == null ? 999 : input.sortOrder(),
                input.enabled() == null || input.enabled(),
                now(),
                now(),
                user(),
                user());
        return ApiResponse.success(type(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Map<String, Object>> updateType(@PathVariable String id, @Valid @RequestBody TypeInput input) {
        Map<String, Object> existing = type(id);
        String code = input.typeCode().trim();
        if (existsType(code, id)) throw BusinessException.conflict("字典类型编码已存在");
        String oldCode = String.valueOf(existing.get("type_code"));
        jdbc.update(
                """
                UPDATE ai_data_dict_type
                SET type_code=?, type_name=?, description=?, sort_order=?, enabled=?, updated_at=?, updated_by=?, lock_version=lock_version+1
                WHERE id=? AND tenant_id=? AND is_deleted=FALSE
                """,
                code,
                input.typeName().trim(),
                blank(input.description()),
                input.sortOrder() == null ? 0 : input.sortOrder(),
                input.enabled() == null || input.enabled(),
                now(),
                user(),
                id,
                tenant());
        if (!oldCode.equals(code)) {
            jdbc.update(
                    "UPDATE ai_data_dict_item SET type_code=?, updated_at=?, updated_by=? WHERE tenant_id=? AND type_code=? AND is_deleted=FALSE",
                    code,
                    now(),
                    user(),
                    tenant(),
                    oldCode);
        }
        return ApiResponse.success(type(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Void> deleteType(@PathVariable String id) {
        Map<String, Object> existing = type(id);
        String code = String.valueOf(existing.get("type_code"));
        jdbc.update(
                "UPDATE ai_data_dict_item SET is_deleted=TRUE, updated_at=?, updated_by=? WHERE tenant_id=? AND type_code=? AND is_deleted=FALSE",
                now(),
                user(),
                tenant(),
                code);
        jdbc.update(
                "UPDATE ai_data_dict_type SET is_deleted=TRUE, updated_at=?, updated_by=? WHERE id=? AND tenant_id=?",
                now(),
                user(),
                id,
                tenant());
        return ApiResponse.success(null);
    }

    @PostMapping("/{typeCode}/items")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Map<String, Object>> createItem(
            @PathVariable String typeCode, @Valid @RequestBody ItemInput input) {
        ensureDefaults();
        requireType(typeCode);
        String code = input.itemCode().trim();
        if (existsItem(typeCode, code, null)) throw BusinessException.conflict("字典项编码已存在");
        String id = id();
        jdbc.update(
                """
                INSERT INTO ai_data_dict_item
                (id,tenant_id,type_code,item_code,item_label,sort_order,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,FALSE,0)
                """,
                id,
                tenant(),
                typeCode.trim(),
                code,
                input.itemLabel().trim(),
                input.sortOrder() == null ? 999 : input.sortOrder(),
                input.enabled() == null || input.enabled(),
                now(),
                now(),
                user(),
                user());
        return ApiResponse.success(item(id));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Map<String, Object>> updateItem(@PathVariable String id, @Valid @RequestBody ItemInput input) {
        Map<String, Object> existing = item(id);
        String typeCode = String.valueOf(existing.get("type_code"));
        String code = input.itemCode().trim();
        if (existsItem(typeCode, code, id)) throw BusinessException.conflict("字典项编码已存在");
        jdbc.update(
                """
                UPDATE ai_data_dict_item
                SET item_code=?, item_label=?, sort_order=?, enabled=?, updated_at=?, updated_by=?, lock_version=lock_version+1
                WHERE id=? AND tenant_id=? AND is_deleted=FALSE
                """,
                code,
                input.itemLabel().trim(),
                input.sortOrder() == null ? 0 : input.sortOrder(),
                input.enabled() == null || input.enabled(),
                now(),
                user(),
                id,
                tenant());
        return ApiResponse.success(item(id));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    @Transactional
    public ApiResponse<Void> deleteItem(@PathVariable String id) {
        item(id);
        jdbc.update(
                "UPDATE ai_data_dict_item SET is_deleted=TRUE, updated_at=?, updated_by=? WHERE id=? AND tenant_id=?",
                now(),
                user(),
                id,
                tenant());
        return ApiResponse.success(null);
    }

    private void ensureDefaults() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_data_dict_type WHERE tenant_id=? AND is_deleted=FALSE",
                Integer.class,
                tenant());
        if (count != null && count > 0) return;
        for (SeedType seed : DEFAULTS.values()) {
            jdbc.update(
                    """
                    INSERT INTO ai_data_dict_type
                    (id,tenant_id,type_code,type_name,description,sort_order,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version)
                    VALUES (?,?,?,?,NULL,?,TRUE,?,?,?,?,FALSE,0)
                    """,
                    id(),
                    tenant(),
                    seed.code(),
                    seed.name(),
                    seed.sort(),
                    now(),
                    now(),
                    user(),
                    user());
            int itemOrder = 0;
            for (String label : seed.items()) {
                jdbc.update(
                        """
                        INSERT INTO ai_data_dict_item
                        (id,tenant_id,type_code,item_code,item_label,sort_order,enabled,created_at,updated_at,created_by,updated_by,is_deleted,lock_version)
                        VALUES (?,?,?,?,?,?,TRUE,?,?,?,?,FALSE,0)
                        """,
                        id(),
                        tenant(),
                        seed.code(),
                        label,
                        label,
                        itemOrder++,
                        now(),
                        now(),
                        user(),
                        user());
            }
        }
    }

    private Map<String, Object> requireType(String typeCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, type_code, type_name FROM ai_data_dict_type WHERE tenant_id=? AND type_code=? AND is_deleted=FALSE",
                tenant(),
                typeCode.trim());
        if (rows.isEmpty()) throw BusinessException.notFound("字典类型不存在");
        return rows.get(0);
    }

    private Map<String, Object> type(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, type_code, type_name, description, sort_order, enabled, lock_version FROM ai_data_dict_type WHERE tenant_id=? AND id=? AND is_deleted=FALSE",
                tenant(),
                id);
        if (rows.isEmpty()) throw BusinessException.notFound("字典类型不存在");
        return rows.get(0);
    }

    private Map<String, Object> item(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, type_code, item_code, item_label, sort_order, enabled, lock_version FROM ai_data_dict_item WHERE tenant_id=? AND id=? AND is_deleted=FALSE",
                tenant(),
                id);
        if (rows.isEmpty()) throw BusinessException.notFound("字典项不存在");
        return rows.get(0);
    }

    private boolean existsType(String code, String excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_data_dict_type WHERE tenant_id=? AND type_code=? AND is_deleted=FALSE",
                        Integer.class,
                        tenant(),
                        code)
                : jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_data_dict_type WHERE tenant_id=? AND type_code=? AND id<>? AND is_deleted=FALSE",
                        Integer.class,
                        tenant(),
                        code,
                        excludeId);
        return count != null && count > 0;
    }

    private boolean existsItem(String typeCode, String code, String excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_data_dict_item WHERE tenant_id=? AND type_code=? AND item_code=? AND is_deleted=FALSE",
                        Integer.class,
                        tenant(),
                        typeCode,
                        code)
                : jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_data_dict_item WHERE tenant_id=? AND type_code=? AND item_code=? AND id<>? AND is_deleted=FALSE",
                        Integer.class,
                        tenant(),
                        typeCode,
                        code,
                        excludeId);
        return count != null && count > 0;
    }

    private static SeedType seed(String code, String name, int sort, String... items) {
        return new SeedType(code, name, sort, List.of(items));
    }

    private record SeedType(String code, String name, int sort, List<String> items) {}

    private String tenant() {
        return RequestContext.tenantId();
    }

    private String user() {
        return RequestContext.userId();
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private static String blank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
