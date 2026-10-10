package cn.gov.document.platform.common.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 单实例开发环境实现；多实例部署必须切换为 Redis。 */
public class InMemoryCoordinationStore implements CoordinationStore {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        Instant now = Instant.now();
        Entry next = new Entry(owner, now.plus(ttl));
        Entry result = entries.compute(
                key,
                (k, current) -> current == null
                                || current.expired(now)
                                || current.value().equals(owner)
                        ? next
                        : current);
        return result == next;
    }

    @Override
    public boolean renew(String key, String owner, Duration ttl) {
        Instant now = Instant.now();
        Entry next = new Entry(owner, now.plus(ttl));
        Entry result = entries.computeIfPresent(
                key, (k, current) -> !current.expired(now) && current.value().equals(owner) ? next : current);
        return result == next;
    }

    @Override
    public boolean unlock(String key, String owner) {
        Instant now = Instant.now();
        boolean[] released = {false};
        entries.computeIfPresent(key, (k, current) -> {
            if (current.expired(now)) return null;
            if (!current.value().equals(owner)) return current;
            released[0] = true;
            return null;
        });
        return released[0];
    }

    @Override
    public Optional<LockInfo> inspect(String key) {
        Entry entry = live(key);
        return entry == null ? Optional.empty() : Optional.of(new LockInfo(entry.value(), entry.expiresAt()));
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        Instant now = Instant.now();
        Entry result = entries.compute(key, (k, current) -> {
            if (current == null || current.expired(now)) return new Entry("1", now.plus(ttlIfNew));
            return new Entry(String.valueOf(Long.parseLong(current.value()) + 1), current.expiresAt());
        });
        return Long.parseLong(result.value());
    }

    @Override
    public boolean exists(String key) {
        return live(key) != null;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    private Entry live(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expired(Instant.now())) {
            entries.remove(key, entry);
            return null;
        }
        return entry;
    }

    private record Entry(String value, Instant expiresAt) {
        boolean expired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
