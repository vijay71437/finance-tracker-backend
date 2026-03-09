package com.financetracker.service.impl;

import com.financetracker.dto.request.LoginRequest;
import com.financetracker.dto.request.RegisterRequest;
import com.financetracker.entity.RefreshToken;
import com.financetracker.entity.User;
import com.financetracker.exception.ConflictException;
import com.financetracker.exception.UnauthorizedException;
import com.financetracker.repository.RefreshTokenRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication service — handles registration, login, token refresh, and logout.
 *
 * <p>Follows the "one service per bounded context" principle.
 * All operations are transactional — failures rollback atomically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().strip())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().strip())
                .phone(request.getPhone())
                .currency(request.getCurrency())
                .timezone(request.getTimezone())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} (uuid={})", user.getEmail(), user.getUuid());

        return buildTokenResponse(user);
    }

    @Transactional
    public Map<String, Object> login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            // Generic message — never reveal which field is wrong
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findActiveByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Account not found or inactive"));

        // Revoke existing refresh tokens to enforce single-session (or adjust for multi-device)
        refreshTokenRepository.revokeAllByUserId(user.getId());

        log.info("User logged in: {} (uuid={})", user.getEmail(), user.getUuid());
        return buildTokenResponse(user);
    }

    @Transactional
    public Map<String, Object> refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token expired or revoked. Please login again.");
        }

        User user = refreshToken.getUser();
        refreshToken.setRevoked(true); // Rotate refresh token
        refreshTokenRepository.save(refreshToken);

        return buildTokenResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User logged out: userId={}", userId);
    }

    // ---- Private helpers ----

    private Map<String, Object> buildTokenResponse(User user) {
        String accessToken = tokenProvider.generateToken(
                user.getEmail(), user.getId(), user.getUuid(), user.getRole());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();
        refreshTokenRepository.save(refreshToken);

        return Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken.getToken(),
                "tokenType",    "Bearer",
                "expiresIn",    refreshExpirationMs / 1000,
                "user",         Map.of(
                        "uuid",     user.getUuid(),
                        "email",    user.getEmail(),
                        "fullName", user.getFullName(),
                        "currency", user.getCurrency()
                )
        );
    }
}