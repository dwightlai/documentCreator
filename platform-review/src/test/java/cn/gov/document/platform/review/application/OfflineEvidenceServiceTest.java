package cn.gov.document.platform.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfflineEvidenceServiceTest {
    @TempDir
    Path directory;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void baselineIsOnlyAnUnverifiedHistoricalIndex() throws Exception {
        RequestContext.set("test", "tenant-a", "admin");
        OfflineEvidenceService service = new OfflineEvidenceService(mapper, directory.toString());
        assertThat(service.info().institutions()).isEqualTo(27);
        assertThat(service.info().policies()).isEqualTo(4);
        assertThat(service.info().verifiedEntries()).isZero();
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setPlainText("依据国发〔2022〕14号办理。");
        var findings = service.inspect(List.of(paragraph), Set.of("L5", "L7"));
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.original()).isEqualTo("国发〔2022〕14号");
            assertThat(finding.explanation()).contains("尚未核验当前效力");
            assertThat(finding.start()).isEqualTo(2);
            assertThat(finding.basis()).contains("本地快照：POLICY-001", "SHA-256");
        });
    }

    @Test
    void importsVerifiedEvidenceAndCanRestorePreviousVersionWithoutCrossTenantLeak() throws Exception {
        RequestContext.set("test", "tenant-a", "admin");
        OfflineEvidenceService service = new OfflineEvidenceService(mapper, directory.toString());
        String baselineDigest = service.info().sha256();
        ObjectNode pack =
                (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/offline-evidence/baseline.json"));
        pack.put("version", "verified-1");
        ObjectNode policy = (ObjectNode) pack.withArray("policies").get(0);
        policy.put("currentStatus", "已废止");
        policy.put("verifiedOn", "2026-11-03");
        policy.put("statusBasisUrl", "https://www.gov.cn/example/repeal.html");
        policy.put("statusBasisSnapshotId", "REPEAL-001");
        String basisText = "测试用公开状态公告";
        ObjectNode statusSnapshot = pack.withArray("sourceSnapshots").addObject();
        statusSnapshot.put("id", "REPEAL-001");
        statusSnapshot.put("sourceUrl", "https://www.gov.cn/example/repeal.html");
        statusSnapshot.put(
                "sha256",
                java.util.HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(basisText.getBytes(StandardCharsets.UTF_8))));
        statusSnapshot.put("text", basisText);
        String importedDigest =
                service.importPack(mapper.writeValueAsBytes(pack)).sha256();
        assertThat(service.info().verifiedEntries()).isEqualTo(1);
        assertThat(service.history())
                .extracting(OfflineEvidenceService.PackInfo::sha256)
                .contains(baselineDigest, importedDigest);
        DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
        paragraph.setPlainText("国发〔2022〕14号");
        assertThat(service.inspect(List.of(paragraph), Set.of("L5")))
                .singleElement()
                .satisfies(finding -> assertThat(finding.explanation()).contains("已废止"));
        service.activate(baselineDigest);
        assertThat(service.info().sha256()).isEqualTo(baselineDigest);
        RequestContext.set("test", "tenant-b", "admin");
        assertThat(service.info().tenantOverride()).isFalse();
        assertThat(service.history())
                .extracting(OfflineEvidenceService.PackInfo::sha256)
                .doesNotContain(importedDigest);
    }

    @Test
    void rejectsUnverifiableStatusAndUnsafeVersionIdentifier() throws Exception {
        RequestContext.set("test", "tenant-a", "admin");
        OfflineEvidenceService service = new OfflineEvidenceService(mapper, directory.toString());
        ObjectNode pack =
                (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/offline-evidence/baseline.json"));
        ((ObjectNode) pack.withArray("policies").get(0)).put("currentStatus", "已废止");
        assertThatThrownBy(() -> service.importPack(mapper.writeValueAsBytes(pack)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.activate("../active")).isInstanceOf(IllegalArgumentException.class);
        ObjectNode snapshot = (ObjectNode) pack.withArray("sourceSnapshots").get(0);
        snapshot.put("text", "tampered");
        assertThatThrownBy(() -> service.importPack(mapper.writeValueAsBytes(pack)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
