package cn.gov.document.platform.common.error;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("RESOURCE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public static BusinessException unprocessable(String message) {
        return new BusinessException("BUSINESS_VALIDATION_FAILED", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
