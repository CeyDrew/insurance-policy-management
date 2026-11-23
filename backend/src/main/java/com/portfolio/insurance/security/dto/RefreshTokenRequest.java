package com.portfolio.insurance.security.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank(message = "Refresh token zorunludur.") String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
