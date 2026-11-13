package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.review.application.deep.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Locally installed, tenant-scoped evidence. A source index never proves current legal effect. */
@Service
public class OfflineEvidenceService {
    private static final int MAX_PACK_BYTES = 10 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final Path root;
    private final byte[] baseline;

    public OfflineEvidenceService(
            ObjectMapper mapper, @Value("${platform.review.offline-evidence-root:./data/offline-review}") String root)
            throws IOException {
        this.mapper = mapper;
        this.root = Path.of(root).toAbsolutePath().normalize();
        try (var input = new ClassPathResource("offline-evidence/baseline.json").getInputStream()) {
            this.baseline = input.readAllBytes();
        }
        validate(this.baseline);
    }

    public PackInfo info() {
        return describe(activeBytes(), Files.exists(tenantFile()));
    }

    public JsonNode exportPack() {
        return parse(activeBytes());
    }

    public List<SourceSnapshot> snapshots() {
        List<SourceSnapshot> result = new ArrayList<>();
        for (JsonNode entry : exportPack().path("sourceSnapshots"))
            result.add(new SourceSnapshot(
                    entry.path("id").asText(),
                    entry.path("sourceUrl").asText(),
                    entry.path("sha256").asText(),
                    entry.path("text").asText()));
        return result;
    }

    private PackInfo describe(byte[] bytes, boolean tenantOverride) {
        JsonNode pack = parse(bytes);
        return new PackInfo(
                pack.path("code").asText(),
                pack.path("version").asText(),
                pack.path("preparedOn").asText(),
                sha256(bytes),
                tenantOverride,
                pack.path("institutions").size(),
                pack.path("positions").size(),
                pack.path("politicalExpressions").size(),
                pack.path("policies").size(),
                countVerified(pack.path("institutions"))
                        + countVerified(pack.path("positions"))
                        + countVerified(pack.path("politicalExpressions"))
                        + countVerified(pack.path("policies")));
    }

    public PackInfo importPack(byte[] bytes) {
        validate(bytes);
        Path target = tenantFile();
        try {
            Files.createDirectories(target.getParent());
            archive(activeBytes());
            archive(bytes);
            Path pending = Files.createTempFile(target.getParent(), "evidence-", ".pending");
            try {
                Files.write(pending, bytes);
                try {
                    Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(pending);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存离线证据包", exception);
        }
        return info();
    }

    public List<PackInfo> history() {
        Path directory = versionsDirectory();
        if (!Files.exists(directory)) return List.of(info());
        try (Stream<Path> files = Files.list(directory)) {
            List<PackInfo> versions = new ArrayList<>();
            for (Path file : files.filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}\\.json"))
                    .toList()) {
                byte[] bytes = Files.readAllBytes(file);
                versions.add(describe(bytes, false));
            }
            versions.sort((left, right) -> right.preparedOn().compareTo(left.preparedOn()));
            return versions;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取离线证据包历史", exception);
        }
    }

    public PackInfo activate(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("版本摘要无效");
        Path version = versionsDirectory().resolve(digest + ".json");
        try {
            if (!Files.exists(versionsDirectory().resolve(digest + ".json")))
                throw new IllegalArgumentException("找不到指定的离线证据包版本");
            byte[] bytes = Files.readAllBytes(version);
            if (!sha256(bytes).equals(digest)) throw new IllegalArgumentException("历史版本校验失败");
            return importPack(bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("找不到指定的离线证据包版本", exception);
        }
    }

    private void archive(byte[] bytes) throws IOException {
        Path directory = versionsDirectory();
        Files.createDirectories(directory);
        Path file = directory.resolve(sha256(bytes) + ".json");
        if (!Files.exists(file)) Files.write(file, bytes);
    }

    private Path versionsDirectory() {
        return tenantFile().getParent().resolve("versions");
    }

    public List<Finding> inspect(List<DocumentParagraphEntity> paragraphs, Set<String> enabledLevels) {
        byte[] bytes = activeBytes();
        JsonNode pack = parse(bytes);
        List<Finding> findings = new ArrayList<>();
        for (DocumentParagraphEntity paragraph : paragraphs) {
            String text = paragraph.getPlainText() == null ? "" : paragraph.getPlainText();
            if (enabledLevels.contains("L5")) {
                inspectPolicies(pack.path("policies"), paragraph, text, findings);
                inspectAliases(
                        pack.path("institutions"),
                        "aliasesToReview",
                        "L5",
                        ReviewCategory.FACT,
                        paragraph,
                        text,
                        findings);
                inspectPositions(pack.path("positions"), paragraph, text, findings);
            }
            if (enabledLevels.contains("L7"))
                inspectAliases(
                        pack.path("politicalExpressions"),
                        "variantsToReview",
                        "L7",
                        ReviewCategory.POLITICAL,
                        paragraph,
                        text,
                        findings);
        }
        String packReference = "；离线证据包：" + pack.path("version").asText() + " / SHA-256 " + sha256(bytes);
        return findings.stream()
                .map(finding -> new Finding(
                        finding.level(),
                        finding.paragraph(),
                        finding.category(),
                        finding.risk(),
                        finding.start(),
                        finding.end(),
                        finding.original(),
                        finding.explanation(),
                        finding.basis() + packReference,
                        finding.confidence()))
                .toList();
    }

    private void inspectPolicies(
            JsonNode policies, DocumentParagraphEntity paragraph, String text, List<Finding> findings) {
        for (JsonNode policy : policies) {
            String number = policy.path("documentNumber").asText();
            if (number.isBlank()) continue;
            String status = policy.path("currentStatus").asText();
            if ("现行".equals(status)) continue;
            int from = 0;
            while ((from = text.indexOf(number, from)) >= 0) {
                String message =
                        switch (status) {
                            case "已废止", "已失效" -> "离线证据包将所引文件标记为" + status + "，请核对引用时点和替代文件。";
                            case "已修改", "已调整" -> "离线证据包记录该文件已有调整，请核对所引条款版本。";
                            default -> "本地只保存该文件的公开来源索引，尚未核验当前效力，请人工核对。";
                        };
                findings.add(finding(
                        "L5",
                        ReviewCategory.FACT,
                        paragraph,
                        from,
                        number,
                        message,
                        basis(policy),
                        "未核验".equals(status) ? "提示" : "重要"));
                from += number.length();
            }
        }
    }

    private void inspectPositions(
            JsonNode positions, DocumentParagraphEntity paragraph, String text, List<Finding> findings) {
        for (JsonNode position : positions) {
            if (!"现行".equals(position.path("currentStatus").asText())) continue;
            String person = position.path("person").asText();
            for (JsonNode oldTitle : position.path("titlesToReview")) {
                String phrase = person + oldTitle.asText();
                addExactMatches(
                        findings,
                        "L5",
                        ReviewCategory.FACT,
                        paragraph,
                        text,
                        phrase,
                        "该姓名与职务组合与离线任职记录不一致；请按公文成文日期和官方任免信息复核。",
                        basis(position),
                        "提示");
            }
        }
    }

    private void inspectAliases(
            JsonNode entries,
            String field,
            String level,
            String category,
            DocumentParagraphEntity paragraph,
            String text,
            List<Finding> findings) {
        for (JsonNode entry : entries) {
            if (!"现行".equals(entry.path("currentStatus").asText())) continue;
            for (JsonNode variant : entry.path(field)) {
                addExactMatches(
                        findings,
                        level,
                        category,
                        paragraph,
                        text,
                        variant.asText(),
                        "该写法与经核验的离线记录不一致，建议核对“" + entry.path("name").asText() + "”。",
                        basis(entry),
                        "提示");
            }
        }
    }

    private void addExactMatches(
            List<Finding> findings,
            String level,
            String category,
            DocumentParagraphEntity paragraph,
            String text,
            String phrase,
            String explanation,
            String basis,
            String risk) {
        if (phrase.isBlank()) return;
        int from = 0;
        while ((from = text.indexOf(phrase, from)) >= 0) {
            findings.add(finding(level, category, paragraph, from, phrase, explanation, basis, risk));
            from += phrase.length();
        }
    }

    private Finding finding(
            String level,
            String category,
            DocumentParagraphEntity paragraph,
            int start,
            String original,
            String explanation,
            String basis,
            String risk) {
        return new Finding(
                level,
                paragraph,
                category,
                risk,
                start,
                start + original.length(),
                original,
                explanation,
                basis,
                new BigDecimal("0.75"));
    }

    private String basis(JsonNode entry) {
        String statusBasis = entry.path("statusBasisUrl").asText("");
        return entry.path("id").asText() + "；"
                + entry.path("sourceTitle").asText(entry.path("title").asText()) + "；原文："
                + entry.path("sourceUrl").asText() + (statusBasis.isBlank() ? "" : "；状态依据：" + statusBasis)
                + "；本地快照：" + entry.path("snapshotId").asText("无")
                + (statusBasis.isBlank()
                        ? ""
                        : "；状态依据快照：" + entry.path("statusBasisSnapshotId").asText("无"))
                + "；核验日：" + entry.path("verifiedOn").asText("未核验");
    }

    private int countVerified(JsonNode entries) {
        int count = 0;
        for (JsonNode entry : entries)
            if (!"未核验".equals(entry.path("currentStatus").asText())) count++;
        return count;
    }

    private byte[] activeBytes() {
        Path file = tenantFile();
        if (!Files.exists(file)) return baseline;
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取已安装的离线证据包", exception);
        }
    }

    private Path tenantFile() {
        String tenant = RequestContext.tenantId();
        if (!tenant.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("租户标识无效");
        Path file = root.resolve(tenant).resolve("active.json").normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("离线证据包路径无效");
        return file;
    }

    private JsonNode parse(byte[] bytes) {
        try {
            return mapper.readTree(bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("离线证据包不是有效 JSON", exception);
        }
    }

    private void validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PACK_BYTES)
            throw new IllegalArgumentException("离线证据包为空或超过 10 MB");
        JsonNode pack = parse(bytes);
        if (!"CN-OFFLINE-REVIEW-EVIDENCE".equals(pack.path("code").asText())
                || pack.path("version").asText().isBlank()
                || !pack.path("preparedOn").isTextual()
                || !pack.path("sourceSnapshots").isArray()
                || !pack.path("institutions").isArray()
                || !pack.path("positions").isArray()
                || !pack.path("politicalExpressions").isArray()
                || !pack.path("policies").isArray()) throw new IllegalArgumentException("离线证据包结构不完整");
        LocalDate.parse(pack.path("preparedOn").asText());
        Set<String> snapshotIds = new HashSet<>();
        Map<String, String> snapshotUrls = new HashMap<>();
        for (JsonNode snapshot : pack.path("sourceSnapshots")) {
            String id = snapshot.path("id").asText();
            String content = snapshot.path("text").asText();
            String sourceUrl = snapshot.path("sourceUrl").asText();
            if (id.isBlank()
                    || !snapshotIds.add(id)
                    || content.isBlank()
                    || !validPublicUrl(sourceUrl)
                    || !sha256(content.getBytes(StandardCharsets.UTF_8))
                            .equals(snapshot.path("sha256").asText()))
                throw new IllegalArgumentException("离线原文快照缺少内容或校验失败：" + id);
            snapshotUrls.put(id, sourceUrl);
        }
        Set<String> ids = new HashSet<>();
        for (String group : List.of("institutions", "positions", "politicalExpressions", "policies")) {
            for (JsonNode entry : pack.path(group)) {
                String status = entry.path("currentStatus").asText();
                String url = entry.path("sourceUrl").asText();
                String id = entry.path("id").asText();
                if (id.isBlank()
                        || !ids.add(id)
                        || !validPublicUrl(url)
                        || !List.of("未核验", "现行", "已调整", "已修改", "已废止", "已失效").contains(status))
                    throw new IllegalArgumentException(
                            "离线证据条目缺少唯一代码、公开来源或状态：" + entry.path("id").asText());
                if (!"未核验".equals(status)
                        && (entry.path("verifiedOn").asText().isBlank()
                                || !validPublicUrl(entry.path("statusBasisUrl").asText())
                                || !url.equals(snapshotUrls.get(
                                        entry.path("snapshotId").asText()))
                                || !entry.path("statusBasisUrl")
                                        .asText()
                                        .equals(snapshotUrls.get(entry.path("statusBasisSnapshotId")
                                                .asText()))))
                    throw new IllegalArgumentException(
                            "已核验状态缺少核验日期或状态依据：" + entry.path("id").asText());
                if (entry.hasNonNull("snapshotId")
                        && !snapshotIds.contains(entry.path("snapshotId").asText()))
                    throw new IllegalArgumentException("找不到离线原文快照：" + id);
                if (entry.hasNonNull("statusBasisSnapshotId")
                        && !snapshotIds.contains(
                                entry.path("statusBasisSnapshotId").asText()))
                    throw new IllegalArgumentException("找不到状态依据快照：" + id);
                if ("institutions".equals(group) || "politicalExpressions".equals(group)) {
                    if (entry.path("name").asText().isBlank()
                            || !entry.path("institutions".equals(group) ? "aliasesToReview" : "variantsToReview")
                                    .isArray()) throw new IllegalArgumentException("机构或重要表述缺少名称或待核对写法：" + id);
                }
                if ("positions".equals(group)
                        && (entry.path("person").asText().isBlank()
                                || !entry.path("titlesToReview").isArray()))
                    throw new IllegalArgumentException("职务记录缺少姓名或待核对职务：" + id);
                if ("policies".equals(group)
                        && (entry.path("title").asText().isBlank()
                                || entry.path("documentNumber").asText().isBlank()))
                    throw new IllegalArgumentException("政策记录缺少标题或文号：" + id);
                if (entry.hasNonNull("statusBasisUrl")
                        && !validPublicUrl(entry.path("statusBasisUrl").asText()))
                    throw new IllegalArgumentException("状态依据必须是公开 HTTPS 地址：" + id);
                if (entry.hasNonNull("verifiedOn"))
                    LocalDate.parse(entry.path("verifiedOn").asText());
                if (entry.hasNonNull("recordedOn"))
                    LocalDate.parse(entry.path("recordedOn").asText());
                if (entry.hasNonNull("issuedOn"))
                    LocalDate.parse(entry.path("issuedOn").asText());
            }
        }
    }

    private boolean validPublicUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String host = uri.getHost();
            return "https".equals(uri.getScheme())
                    && host != null
                    && !host.isBlank()
                    && !"localhost".equalsIgnoreCase(host)
                    && !host.matches("[0-9.]+")
                    && uri.getUserInfo() == null
                    && uri.getPort() == -1;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 不支持 SHA-256", impossible);
        }
    }

    public record PackInfo(
            String code,
            String version,
            String preparedOn,
            String sha256,
            boolean tenantOverride,
            int institutions,
            int positions,
            int politicalExpressions,
            int policies,
            int verifiedEntries) {}

    public record SourceSnapshot(String id, String sourceUrl, String sha256, String text) {}
}
