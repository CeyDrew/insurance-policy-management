package com.portfolio.insurance.security;

import com.portfolio.insurance.security.dto.AuthResponse;
import com.portfolio.insurance.security.dto.LoginRequest;
import com.portfolio.insurance.user.AppUser;
import com.portfolio.insurance.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final TokenService tokenService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        AppUser user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return tokenService.issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }
}
