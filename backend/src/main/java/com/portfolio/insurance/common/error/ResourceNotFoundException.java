package com.portfolio.insurance.common.error;

/**
 * Requested resource does not exist. Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s bulunamadı: %s".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
