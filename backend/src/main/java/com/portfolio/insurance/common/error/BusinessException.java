package com.portfolio.insurance.common.error;

import lombok.Getter;

/**
 * A business rule was violated (e.g. cancelling an expired policy). Mapped to HTTP 422.
 * {@code code} is a stable, machine-readable identifier the frontend can switch on;
 * the message is user-facing (Turkish).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
