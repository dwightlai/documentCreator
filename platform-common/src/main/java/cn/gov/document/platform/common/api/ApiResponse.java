package cn.gov.document.platform.common.api;

import cn.gov.document.platform.common.context.RequestContext;
import java.time.OffsetDateTime;

public record ApiResponse<T>(int code, String message, T data, String requestId, OffsetDateTime timestamp) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, RequestContext.requestId(), OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> accepted(T data) {
        return new ApiResponse<>(202, "accepted", data, RequestContext.requestId(), OffsetDateTime.now());
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, RequestContext.requestId(), OffsetDateTime.now());
    }
}
