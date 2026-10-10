package cn.gov.document.platform.common.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryCoordinationStoreTest {
    private final InMemoryCoordinationStore store = new InMemoryCoordinationStore();

    @Test
    void lockIsExclusiveAndReentrantForSameOwner() {
        assertThat(store.tryLock("k", "alice", Duration.ofMinutes(1))).isTrue();
        assertThat(store.tryLock("k", "bob", Duration.ofMinutes(1))).isFalse();
        assertThat(store.tryLock("k", "alice", Duration.ofMinutes(1))).isTrue();
        assertThat(store.inspect("k"))
                .get()
                .extracting(CoordinationStore.LockInfo::owner)
                .isEqualTo("alice");
    }

    @Test
    void onlyOwnerCanRenewOrUnlock() {
        store.tryLock("k", "alice", Duration.ofMinutes(1));
        assertThat(store.renew("k", "bob", Duration.ofMinutes(5))).isFalse();
        assertThat(store.unlock("k", "bob")).isFalse();
        assertThat(store.renew("k", "alice", Duration.ofMinutes(5))).isTrue();
        assertThat(store.unlock("k", "alice")).isTrue();
        assertThat(store.tryLock("k", "bob", Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void expiredLockCanBeTakenOver() throws InterruptedException {
        store.tryLock("k", "alice", Duration.ofMillis(20));
        Thread.sleep(40);
        assertThat(store.renew("k", "alice", Duration.ofMinutes(1))).isFalse();
        assertThat(store.tryLock("k", "bob", Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void permitsAreLimitedPerWindow() {
        Duration window = Duration.ofMinutes(10);
        assertThat(store.tryAcquirePermit("qps", 2, window)).isTrue();
        assertThat(store.tryAcquirePermit("qps", 2, window)).isTrue();
        assertThat(store.tryAcquirePermit("qps", 2, window)).isFalse();
    }
}
