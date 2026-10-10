package cn.gov.document.platform.common.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** 基于 Redis 的实现，锁的获取、续期与释放均通过 Lua 脚本保证原子性。 */
public class RedisCoordinationStore implements CoordinationStore {
    private static final String PREFIX = "platform:";

    private static final RedisScript<Long> TRY_LOCK = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if (not current) or current == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
              return 1
            end
            return 0
            """,
            Long.class);

    private static final RedisScript<Long> RENEW = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """,
            Long.class);

    private static final RedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private static final RedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            """
            local value = redis.call('INCR', KEYS[1])
            if value == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return value
            """,
            Long.class);

    private final StringRedisTemplate redis;

    public RedisCoordinationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        return Long.valueOf(1).equals(redis.execute(TRY_LOCK, keys(key), owner, millis(ttl)));
    }

    @Override
    public boolean renew(String key, String owner, Duration ttl) {
        return Long.valueOf(1).equals(redis.execute(RENEW, keys(key), owner, millis(ttl)));
    }

    @Override
    public boolean unlock(String key, String owner) {
        return Long.valueOf(1).equals(redis.execute(UNLOCK, keys(key), owner));
    }

    @Override
    public Optional<LockInfo> inspect(String key) {
        String owner = redis.opsForValue().get(PREFIX + key);
        if (owner == null) return Optional.empty();
        Long ttl = redis.getExpire(PREFIX + key, TimeUnit.MILLISECONDS);
        Instant expiresAt = ttl == null || ttl < 0 ? Instant.MAX : Instant.now().plusMillis(ttl);
        return Optional.of(new LockInfo(owner, expiresAt));
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        Long value = redis.execute(INCREMENT, keys(key), millis(ttlIfNew));
        return value == null ? 0 : value;
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + key));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(PREFIX + key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redis.delete(PREFIX + key);
    }

    private static List<String> keys(String key) {
        return List.of(PREFIX + key);
    }

    private static String millis(Duration ttl) {
        return String.valueOf(Math.max(1, ttl.toMillis()));
    }
}
