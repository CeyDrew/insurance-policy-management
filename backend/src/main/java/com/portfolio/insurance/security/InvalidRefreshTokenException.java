package com.portfolio.insurance.security;

import org.springframework.security.core.AuthenticationException;

class InvalidRefreshTokenException extends AuthenticationException {

    InvalidRefreshTokenException(String message) {
        super(message);
    }
}
