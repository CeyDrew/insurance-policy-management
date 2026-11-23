package com.portfolio.insurance.security.dto;

import com.portfolio.insurance.user.UserDto;

/**
 * @param expiresIn access token lifetime in seconds
 */
public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserDto user) {
}
