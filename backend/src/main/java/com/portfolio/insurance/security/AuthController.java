package com.portfolio.insurance.security;

import com.portfolio.insurance.security.dto.AuthResponse;
import com.portfolio.insurance.security.dto.LoginRequest;
import com.portfolio.insurance.security.dto.RefreshTokenRequest;
import com.portfolio.insurance.user.UserDto;
import com.portfolio.insurance.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Login, token refresh and logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Log in with username and password")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Exchange a refresh token for a new token pair (the old refresh token is revoked)")
    @SecurityRequirements
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Revoke a refresh token")
    @SecurityRequirements
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "Current user")
    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
