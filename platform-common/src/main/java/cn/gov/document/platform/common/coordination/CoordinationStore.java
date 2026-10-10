package cn.gov.document.platform.common.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 跨节点协调原语：带租期的互斥锁、计数器与固定窗口限流。
 *
 * <p>生产环境使用 Redis 实现以便多实例共享状态；本地开发使用进程内实现。键的租户隔离由调用方负责。
 */
public interface CoordinationStore {

    /** 获取或续期锁；锁空闲、已过期或已由同一持有人持有时成功。 */
    boolean tryLock(String key, String owner, Duration ttl);

    /** 仅当锁仍由该持有人持有时延长租期。 */
    boolean renew(String key, String owner, Duration ttl);

    /** 仅当锁由该持有人持有时释放。 */
    boolean unlock(String key, String owner);

    Optional<LockInfo> inspect(String key);

    /** 原子自增；键首次创建时设置过期时间。 */
    long increment(String key, Duration ttlIfNew);

    boolean exists(String key);

    void put(String key, String value, Duration ttl);

    void delete(String key);

    /** 固定窗口计数限流：窗口内调用次数未超过 limit 时返回 true。 */
    default boolean tryAcquirePermit(String key, int limit, Duration window) {
        long bucket = Instant.now().toEpochMilli() / Math.max(1, window.toMillis());
        return increment(key + ":" + bucket, window.multipliedBy(2)) <= limit;
    }

    record LockInfo(String owner, Instant expiresAt) {}
}
