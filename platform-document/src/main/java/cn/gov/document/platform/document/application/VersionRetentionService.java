package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.coordination.CoordinationStore;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentVersionEntity;
import cn.gov.document.platform.document.persistence.DocumentMapper;
import cn.gov.document.platform.document.persistence.DocumentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 版本保留策略（§14.4）。
 *
 * <ol>
 *   <li>每日扫描：AI 生成版本超过保留天数或超出每个文稿的最大数量时标记 is_retained=false；
 *   <li>标记满宽限期后物理删除该版本的段落与引用，仅保留版本元数据；
 *   <li>当前版本、管理员保护的版本、人工提交等永久类型、以及仍被审校问题、批注或问答引用的版本永不清理。
 * </ol>
 */
@Service
public class VersionRetentionService {
    private static final Logger log = LoggerFactory.getLogger(VersionRetentionService.class);
    private static final String LOCK_KEY = "retention:document-versions";

    private final DocumentVersionMapper versionMapper;
    private final DocumentMapper documentMapper;
    private final CoordinationStore coordination;
    private final TransactionTemplate transactions;
    private final Map<String, Policy> policies;
    private final Duration purgeGrace;

    public VersionRetentionService(
            DocumentVersionMapper versionMapper,
            DocumentMapper documentMapper,
            CoordinationStore coordination,
            TransactionTemplate transactions,
            @Value("${platform.retention.ai-generated-days:30}") int aiDays,
            @Value("${platform.retention.ai-generated-max:50}") int aiMax,
            @Value("${platform.retention.auto-save-days:7}") int autoSaveDays,
            @Value("${platform.retention.auto-save-max:100}") int autoSaveMax,
            @Value("${platform.retention.purge-grace-days:7}") int graceDays) {
        this.versionMapper = versionMapper;
        this.documentMapper = documentMapper;
        this.coordination = coordination;
        this.transactions = transactions;
        this.policies = Map.of(
                "AI生成", new Policy(Duration.ofDays(aiDays), aiMax),
                "自动保存", new Policy(Duration.ofDays(autoSaveDays), autoSaveMax));
        this.purgeGrace = Duration.ofDays(graceDays);
    }

    /** 每日凌晨执行；多实例部署时通过协调锁保证只有一个节点运行。 */
    @Scheduled(cron = "${platform.retention.cron:0 30 2 * * *}")
    public void runForAllTenants() {
        String owner = UUID.randomUUID().toString();
        if (!coordination.tryLock(LOCK_KEY, owner, Duration.ofHours(1))) return;
        try {
            for (String tenant : versionMapper.findTenantIds()) {
                RequestContext.set("retention-" + owner, tenant, "system");
                try {
                    Result result = runForCurrentTenant();
                    if (result.marked() + result.purged() > 0)
                        log.info("版本保留策略 tenant={} 标记 {} 个、清理 {} 个", tenant, result.marked(), result.purged());
                } catch (RuntimeException e) {
                    log.error("版本保留策略执行失败 tenant={}", tenant, e);
                } finally {
                    RequestContext.clear();
                }
            }
        } finally {
            coordination.unlock(LOCK_KEY, owner);
        }
    }

    /** 对当前请求上下文中的租户执行一次标记与清理，供定时任务与运维接口调用。 */
    public Result runForCurrentTenant() {
        int marked = transactions.execute(status -> mark(OffsetDateTime.now()));
        int purged = transactions.execute(status -> purge(OffsetDateTime.now()));
        return new Result(marked, purged);
    }

    private int mark(OffsetDateTime now) {
        Map<String, Integer> currentVersions =
                documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()).stream()
                        .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getCurrentVersion));
        Map<String, List<DocumentVersionEntity>> byDocument = versionMapper
                .selectList(new LambdaQueryWrapper<DocumentVersionEntity>()
                        .in(DocumentVersionEntity::getVersionType, policies.keySet())
                        .eq(DocumentVersionEntity::getIsRetained, true))
                .stream()
                .collect(Collectors.groupingBy(DocumentVersionEntity::getDocumentId));
        int marked = 0;
        for (var entry : byDocument.entrySet()) {
            Integer current = currentVersions.get(entry.getKey());
            for (String type : policies.keySet()) {
                Policy policy = policies.get(type);
                List<DocumentVersionEntity> versions = entry.getValue().stream()
                        .filter(v -> type.equals(v.getVersionType()))
                        .sorted((a, b) -> b.getVersionNumber() - a.getVersionNumber())
                        .toList();
                for (int index = 0; index < versions.size(); index++) {
                    DocumentVersionEntity version = versions.get(index);
                    boolean expired = version.getCreatedAt().isBefore(now.minus(policy.maxAge()));
                    boolean overflow = index >= policy.maxCount();
                    if (!(expired || overflow) || !purgeable(version, current)) continue;
                    version.setIsRetained(false);
                    version.setRetentionMarkedAt(now);
                    versionMapper.updateById(version);
                    marked++;
                }
            }
        }
        return marked;
    }

    private int purge(OffsetDateTime now) {
        Map<String, Integer> currentVersions =
                documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()).stream()
                        .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getCurrentVersion));
        int purged = 0;
        for (DocumentVersionEntity version : versionMapper.selectList(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getIsRetained, false)
                .isNull(DocumentVersionEntity::getContentPurgedAt)
                .le(DocumentVersionEntity::getRetentionMarkedAt, now.minus(purgeGrace)))) {
            // 宽限期内可能被管理员保护、被引用或重新成为当前版本，清理前再次确认
            if (!purgeable(version, currentVersions.get(version.getDocumentId()))) {
                version.setIsRetained(true);
                version.setRetentionMarkedAt(null);
                versionMapper.updateById(version);
                continue;
            }
            versionMapper.purgeCitations(version.getDocumentId(), version.getVersionNumber());
            versionMapper.purgeParagraphs(version.getDocumentId(), version.getVersionNumber());
            version.setContentPurgedAt(now);
            versionMapper.updateById(version);
            purged++;
        }
        return purged;
    }

    private boolean purgeable(DocumentVersionEntity version, Integer currentVersion) {
        if (Boolean.TRUE.equals(version.getIsProtected())) return false;
        if (currentVersion != null && currentVersion.equals(version.getVersionNumber())) return false;
        return versionMapper.countReferences(version.getDocumentId(), version.getVersionNumber()) == 0;
    }

    public record Result(int marked, int purged) {}

    private record Policy(Duration maxAge, int maxCount) {}
}
