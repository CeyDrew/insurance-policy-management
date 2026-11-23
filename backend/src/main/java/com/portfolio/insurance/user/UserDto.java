package com.portfolio.insurance.user;

public record UserDto(Long id, String username, String fullName, String email, Role role, boolean active) {
}
