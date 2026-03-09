package com.financetracker.service;

import com.financetracker.dto.request.LoginRequest;
import com.financetracker.dto.request.RegisterRequest;
import com.financetracker.entity.RefreshToken;
import com.financetracker.entity.User;
import com.financetracker.exception.ConflictException;
import com.financetracker.exception.UnauthorizedException;
import com.financetracker.repository.RefreshTokenRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.security.jwt.JwtTokenProvider;
import com.financetracker.service.impl.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register new user and return tokens")
        void register_newEmail_returnsTokens() {
            // Arrange
            RegisterRequest request = buildRegisterRequest("new@example.com");

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("jwt.access.token");

            // Act
            Map<String, Object> result = authService.register(request);

            // Assert
            assertThat(result).containsKey("accessToken");
            assertThat(result).containsKey("refreshToken");
            assertThat(result.get("accessToken")).isEqualTo("jwt.access.token");

            // Verify password was encoded
            verify(passwordEncoder).encode("SecurePass@1");

            // Verify user was saved with lowercase email
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("should throw ConflictException when email already exists")
        void register_duplicateEmail_throwsConflictException() {
            // Arrange
            RegisterRequest request = buildRegisterRequest("existing@example.com");
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should normalize email to lowercase before saving")
        void register_uppercaseEmail_savesLowercase() {
            // Arrange
            RegisterRequest request = buildRegisterRequest("User@Example.COM");
            when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("token");

            // Act
            authService.register(request);

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return tokens on successful login")
        void login_validCredentials_returnsTokens() {
            // Arrange
            User user = buildUser(1L, "user@example.com");
            LoginRequest request = new LoginRequest();
            request.setEmail("user@example.com");
            request.setPassword("password");

            when(authenticationManager.authenticate(any())).thenReturn(null);
            when(userRepository.findActiveByEmail("user@example.com"))
                    .thenReturn(Optional.of(user));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("access.token");

            // Act
            Map<String, Object> result = authService.login(request);

            // Assert
            assertThat(result.get("accessToken")).isEqualTo("access.token");

            // Existing tokens should be revoked on fresh login
            verify(refreshTokenRepository).revokeAllByUserId(user.getId());
        }

        @Test
        @DisplayName("should throw UnauthorizedException for bad credentials")
        void login_badCredentials_throwsUnauthorizedException() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("user@example.com");
            request.setPassword("wrongpass");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password"); // Generic message — no field disclosure
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenTests {

        @Test
        @DisplayName("should return new tokens for valid refresh token")
        void refreshToken_validToken_returnsNewTokens() {
            // Arrange
            User user = buildUser(1L, "user@example.com");
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L).user(user)
                    .token("valid-refresh-token")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken("valid-refresh-token"))
                    .thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("new.access.token");

            // Act
            Map<String, Object> result = authService.refreshToken("valid-refresh-token");

            // Assert
            assertThat(result.get("accessToken")).isEqualTo("new.access.token");
            assertThat(refreshToken.isRevoked()).isTrue(); // Old token rotated
        }

        @Test
        @DisplayName("should throw UnauthorizedException for expired refresh token")
        void refreshToken_expiredToken_throwsUnauthorizedException() {
            // Arrange
            User user = buildUser(1L, "user@example.com");
            RefreshToken expiredToken = RefreshToken.builder()
                    .id(1L).user(user)
                    .token("expired-token")
                    .expiresAt(Instant.now().minusSeconds(3600)) // already expired
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken("expired-token"))
                    .thenReturn(Optional.of(expiredToken));

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshToken("expired-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("expired or revoked");
        }

        @Test
        @DisplayName("should throw UnauthorizedException for non-existent token")
        void refreshToken_notFound_throwsUnauthorizedException() {
            when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("unknown"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid refresh token");
        }
    }

    // ---- Helpers ----

    private RegisterRequest buildRegisterRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("SecurePass@1");
        req.setFullName("Test User");
        req.setCurrency("USD");
        req.setTimezone("UTC");
        return req;
    }

    private User buildUser(Long id, String email) {
        return User.builder()
                .id(id).uuid("user-uuid-" + id)
                .email(email)
                .passwordHash("$2a$12$hashed")
                .fullName("Test User")
                .currency("USD").timezone("UTC")
                .role("ROLE_USER").isActive(true)
                .build();
    }
}