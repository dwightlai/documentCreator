package cn.gov.document.platform.common.context;

import java.util.Optional;

public final class RequestContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(String requestId, String tenantId, String userId) {
        CURRENT.set(new Context(requestId, tenantId, userId));
    }

    public static String requestId() {
        return Optional.ofNullable(CURRENT.get()).map(Context::requestId).orElse("unknown");
    }

    public static String tenantId() {
        return Optional.ofNullable(CURRENT.get()).map(Context::tenantId)
            .orElseThrow(() -> new IllegalStateException("缺少租户上下文"));
    }

    public static String userId() {
        return Optional.ofNullable(CURRENT.get()).map(Context::userId).orElse("anonymous");
    }

    public static void clear() {
        CURRENT.remove();
    }

    private record Context(String requestId, String tenantId, String userId) {
    }
}
