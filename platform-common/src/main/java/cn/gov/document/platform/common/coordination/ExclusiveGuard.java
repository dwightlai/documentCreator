package cn.gov.document.platform.common.coordination;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 业务互斥：锁在当前事务结束（提交或回滚）后才释放，避免事务未提交时其他节点读到旧状态。
 *
 * <p>同一事务内对同一键重复加锁直接通过（可重入）。TTL 只是节点崩溃时的兜底，正常情况下锁随事务结束释放。
 */
@Component
public class ExclusiveGuard {
    private final CoordinationStore store;

    public ExclusiveGuard(CoordinationStore store) {
        this.store = store;
    }

    public void holdUntilTransactionEnd(String key, Duration ttl, String conflictMessage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("holdUntilTransactionEnd 必须在事务内调用");
        String scopedKey = tenantKey(key);
        Set<String> held = heldKeys();
        if (held.contains(scopedKey)) return;
        String owner = UUID.randomUUID().toString();
        if (!store.tryLock(scopedKey, owner, ttl)) throw BusinessException.conflict(conflictMessage);
        held.add(scopedKey);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                store.unlock(scopedKey, owner);
            }
        });
    }

    public static String tenantKey(String key) {
        return "t:" + RequestContext.tenantId() + ":" + key;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> heldKeys() {
        Object resource = TransactionSynchronizationManager.getResource(ExclusiveGuard.class);
        if (resource != null) return (Set<String>) resource;
        Set<String> held = new HashSet<>();
        TransactionSynchronizationManager.bindResource(ExclusiveGuard.class, held);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(ExclusiveGuard.class);
            }
        });
        return held;
    }
}
