package cn.gov.document.platform.model.application;

import cn.gov.document.platform.common.coordination.CoordinationStore;
import cn.gov.document.platform.common.coordination.ExclusiveGuard;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按模型配置维度的熔断器，状态保存在协调存储中以便多实例共享。
 *
 * <p>统计窗口内连续失败达到阈值后熔断，冷却期内路由跳过该模型并改用备用模型；冷却期结束后自动半开，下一次调用成功即清零。
 */
@Component
public class ModelCircuitBreaker {
    private final CoordinationStore store;
    private final int failureThreshold;
    private final Duration failureWindow;
    private final Duration openDuration;

    public ModelCircuitBreaker(
            CoordinationStore store,
            @Value("${platform.model.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${platform.model.circuit-breaker.failure-window:60s}") Duration failureWindow,
            @Value("${platform.model.circuit-breaker.open-duration:30s}") Duration openDuration) {
        this.store = store;
        this.failureThreshold = failureThreshold;
        this.failureWindow = failureWindow;
        this.openDuration = openDuration;
    }

    public boolean isOpen(String configId) {
        return store.exists(openKey(configId));
    }

    public void recordSuccess(String configId) {
        store.delete(failureKey(configId));
    }

    public void recordFailure(String configId) {
        long failures = store.increment(failureKey(configId), failureWindow);
        if (failures >= failureThreshold) {
            store.put(openKey(configId), "open", openDuration);
            store.delete(failureKey(configId));
        }
    }

    private static String failureKey(String configId) {
        return ExclusiveGuard.tenantKey("model:" + configId + ":failures");
    }

    private static String openKey(String configId) {
        return ExclusiveGuard.tenantKey("model:" + configId + ":circuit-open");
    }
}
