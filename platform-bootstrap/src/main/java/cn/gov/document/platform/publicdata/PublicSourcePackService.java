package cn.gov.document.platform.publicdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Curated public-source cards. Installation is additive and never replaces tenant edits. */
@Service
public class PublicSourcePackService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final JsonNode pack;
    private final JsonNode offlineManifest;
    private final Map<String, OfflineDocument> offlineByUrl = new HashMap<>();

    public PublicSourcePackService(JdbcTemplate jdbc, ObjectMapper mapper) throws IOException {
        this.jdbc = jdbc;
        this.mapper = mapper;
        try (var input = new ClassPathResource("public-data/public-source-pack.json").getInputStream()) {
            pack = mapper.readTree(input);
        }
        try (var input = new ClassPathResource("public-data/offline-manifest.json").getInputStream()) {
            offlineManifest = mapper.readTree(input);
        }
        for (JsonNode item : offlineManifest.path("documents")) {
            String file = item.path("file").asText();
            String content;
            try (var input = new ClassPathResource("public-data/" + file).getInputStream()) {
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!sha256(content).equals(item.path("textSha256").asText()))
                throw new IllegalStateException("离线原文校验失败：" + file);
            offlineByUrl.put(item.path("url").asText(), new OfflineDocument(item, content));
        }
        validate();
    }

    public PackInfo info(String tenantId) {
        int count = 0;
        for (JsonNode category : pack.path("categories"))
            count += category.path("entries").size();
        boolean installed = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM public_source_pack_install WHERE tenant_id=? AND pack_code=? AND pack_version=?",
                        Integer.class,
                        tenantId,
                        code(),
                        version())
                > 0;
        return new PackInfo(
                code(), version(), pack.path("sourceCheckedOn").asText(), 5, count + offlineByUrl.size(), installed);
    }

    @Transactional
    public InstallResult install(String tenantId, String actor) {
        if (info(tenantId).installed()) return new InstallResult(false, 0, 0);
        OffsetDateTime now = OffsetDateTime.now();
        int newSources = 0, newDocuments = 0;
        for (JsonNode category : pack.path("categories")) {
            String sourceCode = category.path("code").asText();
            String sourceId = jdbc.query(
                    "SELECT source_id FROM public_source_pack_source WHERE tenant_id=? AND source_code=?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    tenantId,
                    sourceCode);
            if (sourceId == null) {
                Integer conflicting = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_knowledge_source WHERE tenant_id=? AND source_code=?",
                        Integer.class,
                        tenantId,
                        sourceCode);
                if (conflicting != null && conflicting > 0) throw new IllegalStateException("知识库代码已被占用：" + sourceCode);
                sourceId = id();
                jdbc.update(
                        "INSERT INTO ai_knowledge_source (id,tenant_id,source_code,source_name,category,storage_type,permission_scope,entry_count,last_sync_time,is_active,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,?,?,?,TRUE,?,?,?,?,FALSE,0)",
                        sourceId,
                        tenantId,
                        sourceCode,
                        category.path("name").asText(),
                        category.path("name").asText(),
                        "全文索引",
                        "公开",
                        0,
                        now,
                        now,
                        now,
                        actor,
                        actor);
                jdbc.update(
                        "INSERT INTO public_source_pack_source (tenant_id,source_code,source_id) VALUES (?,?,?)",
                        tenantId,
                        sourceCode,
                        sourceId);
                newSources++;
            }
            for (JsonNode entry : category.path("entries")) {
                String entryCode = entry.path("id").asText();
                String existingId = jdbc.query(
                        "SELECT document_id FROM public_source_pack_entry WHERE tenant_id=? AND entry_code=?",
                        rs -> rs.next() ? rs.getString(1) : null,
                        tenantId,
                        entryCode);
                if (existingId != null) {
                    upgradeCard(tenantId, existingId, entry, actor, now);
                    jdbc.update(
                            "UPDATE public_source_pack_entry SET installed_version=? WHERE tenant_id=? AND entry_code=?",
                            version(),
                            tenantId,
                            entryCode);
                    continue;
                }
                String documentId = id();
                String content = card(entry);
                String hash = sha256(content);
                var metadata = mapper.createObjectNode();
                metadata.put("publicSourceId", entryCode);
                metadata.put("sourceUrl", entry.path("url").asText());
                metadata.put("publisher", entry.path("publisher").asText());
                metadata.put("documentNumber", entry.path("documentNumber").asText());
                metadata.put("issuedOn", entry.path("issuedOn").asText());
                metadata.put("sourceCheckedOn", pack.path("sourceCheckedOn").asText());
                metadata.put("reviewStatus", "公开页面来源已核验；效力及具体适用仍需人工确认");
                metadata.put(
                        "offlineContentType",
                        offlineByUrl.containsKey(entry.path("url").asText()) ? "原文摘录" : "来源索引");
                jdbc.update(
                        "INSERT INTO ai_knowledge_document (id,tenant_id,source_id,document_title,original_file_hash,parsed_status,metadata,authority_level,permission_level,chunk_count,document_version,extracted_text,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,'已完成',?,'官方公开资料','公开',1,1,?,?,?,?,?,FALSE,0)",
                        documentId,
                        tenantId,
                        sourceId,
                        entry.path("title").asText(),
                        hash,
                        metadata.toString(),
                        content,
                        now,
                        now,
                        actor,
                        actor);
                jdbc.update(
                        "INSERT INTO ai_knowledge_chunk (id,tenant_id,document_id,chunk_order,chunk_text,chunk_text_hash,keywords,start_position,end_position,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,1,?,?,'[]',0,?,?,?,?,?,FALSE,0)",
                        id(),
                        tenantId,
                        documentId,
                        content,
                        hash,
                        content.length(),
                        now,
                        now,
                        actor,
                        actor);
                jdbc.update(
                        "INSERT INTO public_source_pack_entry (tenant_id,entry_code,document_id,installed_version) VALUES (?,?,?,?)",
                        tenantId,
                        entryCode,
                        documentId,
                        version());
                newDocuments++;
            }
            for (OfflineDocument offline : offlineByUrl.values()) {
                JsonNode item = offline.manifest();
                if (!sourceCode.equals(item.path("categoryCode").asText())) continue;
                String entryCode = "OFFLINE-" + item.path("entryId").asText();
                String existingId = jdbc.query(
                        "SELECT document_id FROM public_source_pack_entry WHERE tenant_id=? AND entry_code=?",
                        rs -> rs.next() ? rs.getString(1) : null,
                        tenantId,
                        entryCode);
                if (existingId != null) {
                    String formerTitle =
                            "离线原文 · " + item.path("sourceCardTitle").asText();
                    String currentTitle = "离线原文 · " + item.path("title").asText();
                    if (!formerTitle.equals(currentTitle))
                        jdbc.update(
                                "UPDATE ai_knowledge_document SET document_title=?,updated_at=?,updated_by=? WHERE tenant_id=? AND id=? AND document_title=? AND original_file_hash=?",
                                currentTitle,
                                now,
                                actor,
                                tenantId,
                                existingId,
                                formerTitle,
                                item.path("textSha256").asText());
                    continue;
                }
                installOfflineDocument(tenantId, sourceId, entryCode, offline, actor, now);
                newDocuments++;
            }
            jdbc.update(
                    "UPDATE ai_knowledge_source SET entry_count=(SELECT COUNT(*) FROM ai_knowledge_document WHERE tenant_id=? AND source_id=? AND is_deleted=FALSE),last_sync_time=?,updated_at=? WHERE id=? AND tenant_id=?",
                    tenantId,
                    sourceId,
                    now,
                    now,
                    sourceId,
                    tenantId);
        }
        jdbc.update(
                "INSERT INTO public_source_pack_install (tenant_id,pack_code,pack_version,installed_at,installed_by) VALUES (?,?,?,?,?)",
                tenantId,
                code(),
                version(),
                now,
                actor);
        return new InstallResult(true, newSources, newDocuments);
    }

    private void upgradeCard(String tenantId, String documentId, JsonNode entry, String actor, OffsetDateTime now) {
        ExistingCard existing = jdbc.query(
                "SELECT d.original_file_hash,c.id,c.chunk_text,d.metadata FROM ai_knowledge_document d JOIN ai_knowledge_chunk c ON c.document_id=d.id AND c.is_deleted=FALSE WHERE d.tenant_id=? AND d.id=? AND d.is_deleted=FALSE AND c.chunk_order=1",
                rs -> rs.next()
                        ? new ExistingCard(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))
                        : null,
                tenantId,
                documentId);
        String old = sourceCard(entry);
        if (existing == null || !sha256(old).equals(existing.hash()) || !old.equals(existing.chunkText())) return;
        String changed = card(entry);
        String hash = sha256(changed);
        ObjectNode metadata;
        try {
            metadata = (ObjectNode) mapper.readTree(existing.metadata());
        } catch (IOException e) {
            throw new IllegalStateException("公开来源元数据损坏：" + documentId, e);
        }
        metadata.put(
                "offlineContentType", offlineByUrl.containsKey(entry.path("url").asText()) ? "原文摘录" : "来源索引");
        jdbc.update(
                "UPDATE ai_knowledge_document SET original_file_hash=?,extracted_text=?,metadata=?,document_version=document_version+1,updated_at=?,updated_by=? WHERE tenant_id=? AND id=?",
                hash,
                changed,
                metadata.toString(),
                now,
                actor,
                tenantId,
                documentId);
        jdbc.update(
                "UPDATE ai_knowledge_chunk SET chunk_text=?,chunk_text_hash=?,end_position=?,updated_at=?,updated_by=? WHERE tenant_id=? AND id=?",
                changed,
                hash,
                changed.length(),
                now,
                actor,
                tenantId,
                existing.chunkId());
    }

    private void installOfflineDocument(
            String tenantId,
            String sourceId,
            String entryCode,
            OfflineDocument offline,
            String actor,
            OffsetDateTime now) {
        JsonNode item = offline.manifest();
        String text = offline.text();
        String documentId = id();
        List<TextPart> parts = splitText(text);
        var metadata = mapper.createObjectNode();
        metadata.put("publicSourceId", item.path("entryId").asText());
        metadata.put("sourceUrl", item.path("url").asText());
        metadata.put("publisher", item.path("publisher").asText());
        metadata.put("documentNumber", item.path("documentNumber").asText());
        metadata.put("issuedOn", item.path("issuedOn").asText());
        metadata.put("sourceCheckedOn", offlineManifest.path("extractedOn").asText());
        metadata.put("offlineContentType", "公开页正文");
        metadata.put("completeness", item.path("completeness").asText());
        metadata.put("sourceTextSha256", item.path("textSha256").asText());
        jdbc.update(
                "INSERT INTO ai_knowledge_document (id,tenant_id,source_id,document_title,original_file_hash,parsed_status,metadata,authority_level,permission_level,chunk_count,document_version,extracted_text,completeness,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,'已完成',?,'官方公开资料','公开',?,1,?,'本地原文可用',?,?,?,?,FALSE,0)",
                documentId,
                tenantId,
                sourceId,
                "离线原文 · " + item.path("title").asText(),
                sha256(text),
                metadata.toString(),
                parts.size(),
                text,
                now,
                now,
                actor,
                actor);
        int order = 1;
        for (TextPart part : parts) {
            jdbc.update(
                    "INSERT INTO ai_knowledge_chunk (id,tenant_id,document_id,chunk_order,chunk_text,chunk_text_hash,keywords,start_position,end_position,created_at,updated_at,created_by,updated_by,is_deleted,lock_version) VALUES (?,?,?,?,?,?,'[]',?,?,?,?,?,?,FALSE,0)",
                    id(),
                    tenantId,
                    documentId,
                    order++,
                    part.text(),
                    sha256(part.text()),
                    part.start(),
                    part.end(),
                    now,
                    now,
                    actor,
                    actor);
        }
        jdbc.update(
                "INSERT INTO public_source_pack_entry (tenant_id,entry_code,document_id,installed_version) VALUES (?,?,?,?)",
                tenantId,
                entryCode,
                documentId,
                version());
    }

    private static List<TextPart> splitText(String text) {
        List<TextPart> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + 1800);
            if (end < text.length()) {
                int breakAt = text.lastIndexOf('\n', end);
                if (breakAt > start + 700) end = breakAt + 1;
            }
            parts.add(new TextPart(start, end, text.substring(start, end)));
            start = end;
        }
        return parts;
    }

    private String card(JsonNode entry) {
        String base = sourceCard(entry);
        OfflineDocument offline = offlineByUrl.get(entry.path("url").asText());
        if (offline == null) return base + "\n内容状态：仅来源索引，未保存可离线核对的正文。";
        String text = offline.text();
        String marker = entry.path("checkText").asText();
        int at = marker.isBlank() ? 0 : text.indexOf(marker);
        if (at < 0) at = 0;
        if (entry.path("id").asText().startsWith("FACT-")) {
            int lineStart = text.lastIndexOf('\n', Math.max(0, at - 1)) + 1;
            at = lineStart;
        }
        int end = Math.min(text.length(), at + (entry.path("id").asText().startsWith("FACT-") ? 450 : 1100));
        return base + "\n离线原文摘录：\n" + text.substring(at, end).trim()
                + (end < text.length() ? "\n……（完整公开页正文见本分类的「离线原文」文档）" : "");
    }

    private String sourceCard(JsonNode entry) {
        return entry.path("title").asText() + "\n发布机构："
                + entry.path("publisher").asText()
                + "\n文号：" + entry.path("documentNumber").asText()
                + "\n发文或公开日期：" + entry.path("issuedOn").asText()
                + "\n内容摘要：" + entry.path("summary").asText()
                + "\n使用范围：" + entry.path("usage").asText()
                + "\n公开原文：" + entry.path("url").asText()
                + "\n提示：使用前核对原文、适用范围与现行效力。";
    }

    private void validate() {
        if (code().isBlank() || version().isBlank() || pack.path("categories").size() != 5)
            throw new IllegalStateException("公开来源包结构无效");
        if (!version().equals(offlineManifest.path("version").asText()) || offlineByUrl.size() != 8)
            throw new IllegalStateException("离线原文清单与来源包版本不一致");
        Set<String> codes = new HashSet<>();
        Set<String> entries = new HashSet<>();
        for (JsonNode category : pack.path("categories")) {
            if (!codes.add(category.path("code").asText())
                    || !category.path("entries").isArray()
                    || category.path("entries").isEmpty()) throw new IllegalStateException("公开来源分类无效");
            for (JsonNode entry : category.path("entries")) {
                if (!entries.add(entry.path("id").asText())
                        || entry.path("title").asText().isBlank()
                        || entry.path("summary").asText().isBlank()
                        || entry.path("publisher").asText().isBlank()
                        || !entry.path("url").asText().startsWith("https://"))
                    throw new IllegalStateException(
                            "公开来源条目无效：" + entry.path("id").asText());
            }
        }
    }

    private String code() {
        return pack.path("code").asText();
    }

    private String version() {
        return pack.path("version").asText();
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record PackInfo(
            String code, String version, String sourceCheckedOn, int categories, int entries, boolean installed) {}

    public record InstallResult(boolean installed, int sources, int documents) {}

    private record OfflineDocument(JsonNode manifest, String text) {}

    private record TextPart(int start, int end, String text) {}

    private record ExistingCard(String hash, String chunkId, String chunkText, String metadata) {}
}
