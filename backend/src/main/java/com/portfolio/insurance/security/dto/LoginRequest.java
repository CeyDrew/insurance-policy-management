package com.portfolio.insurance.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Kullanıcı adı zorunludur.")
        @Size(max = 50, message = "Kullanıcı adı en fazla 50 karakter olabilir.")
        String username,

        @NotBlank(message = "Şifre zorunludur.")
        @Size(max = 100, message = "Şifre en fazla 100 karakter olabilir.")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
