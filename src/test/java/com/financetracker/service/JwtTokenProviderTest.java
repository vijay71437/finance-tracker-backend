package com.financetracker.service;

import com.financetracker.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String SECRET =
            "test-secret-key-must-be-at-least-256-bits-for-hs256-algorithm";
    private static final long EXPIRATION_MS = 3600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken - should produce a valid token")
    void generateToken_validInputs_producesToken() {
        String token = tokenProvider.generateToken("user@test.com", 1L, "uuid-1", "ROLE_USER");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    @DisplayName("extractEmail - should extract correct subject from token")
    void extractEmail_validToken_returnsEmail() {
        String token = tokenProvider.generateToken("user@test.com", 1L, "uuid-1", "ROLE_USER");
        assertThat(tokenProvider.extractEmail(token)).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("validateToken - should return true for valid token")
    void validateToken_validToken_returnsTrue() {
        String token = tokenProvider.generateToken("user@test.com", 1L, "uuid-1", "ROLE_USER");
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken - tampered token - should return false")
    void validateToken_tamperedToken_returnsFalse() {
        // Generate a valid token
        String validToken = tokenProvider.generateToken(
                "test@test.com", 1L, "uuid-123", "ROLE_USER");

        // Tamper with it — change last few characters
        String tamperedToken = validToken.substring(0, validToken.length() - 10) + "tampered!!";

        // validateToken() should return false — NOT throw an exception
        // because it catches SignatureException internally
        boolean result = tokenProvider.validateToken(tamperedToken);

        assertThat(result).isFalse();  // ← this is what the test should assert
    }

    @Test
    @DisplayName("validateToken - should return false for expired token")
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L); // 1ms expiry
        String token = shortLivedProvider.generateToken("user@test.com", 1L, "uuid-1", "ROLE_USER");

        // Wait briefly for expiry
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken - should return false for blank token")
    void validateToken_blankToken_returnsFalse() {
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("extractUserId - should extract userId from claims")
    void extractUserId_validToken_returnsUserId() {
        String token = tokenProvider.generateToken("user@test.com", 42L, "uuid-42", "ROLE_USER");
        assertThat(tokenProvider.extractUserId(token)).isEqualTo(42L);
    }
}