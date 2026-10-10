package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AccountLockedException;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.TokenExpiredException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.RefreshToken;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.RefreshTokenRepository;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.identity.security.JwtService;
import com.lankapay.identity.web.RegisterRequest;
import com.lankapay.identity.web.RegisterResponse;
import com.lankapay.identity.web.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * All collaborators are mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository          userRepository;
    @Mock RefreshTokenRepository  refreshTokenRepository;
    @Mock PasswordEncoder         passwordEncoder;
    @Mock JwtService              jwtService;
    @Mock OtpService              otpService;
    @Mock AuditService            auditService;

    AuthService authService;

    static final String IP = "127.0.0.1";
    static final String PHONE = "+94771234567";
    static final String PIN   = "123456";
    static final String HASHED_PIN = "$2a$12$hashedpin";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository,
                passwordEncoder, jwtService, otpService, auditService,
                5,    // maxAttempts
                15L,  // lockoutMinutes
                7L,   // refreshTokenExpiryDays
                30L   // jwtExpiryMinutes
        );
    }

    // ── Helpers ────────────────────────────────────────────────

    User activeUser() {
        User u = new User(PHONE, "Kamal Perera", "199012345678", HASHED_PIN);
        ReflectionTestUtils.setField(u, "id", 1L);
        u.setStatus(UserStatus.ACTIVE);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    User pendingUser() {
        User u = new User(PHONE, "Kamal Perera", "199012345678", HASHED_PIN);
        ReflectionTestUtils.setField(u, "id", 1L);
        // status defaults to PENDING_VERIFICATION
        return u;
    }

    // ── Register ───────────────────────────────────────────────

    @Nested @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("success – creates user and returns userId + OTP expiry")
        void success() {
            when(userRepository.existsByPhone(PHONE)).thenReturn(false);
            when(userRepository.existsByNicNumber(any())).thenReturn(false);
            when(passwordEncoder.encode(PIN)).thenReturn(HASHED_PIN);

            // Return a user that already has id set (simulates DB auto-increment)
            User saved = pendingUser(); // id=1L set via ReflectionTestUtils
            when(userRepository.save(any(User.class))).thenReturn(saved);

            Instant expiry = Instant.now().plusSeconds(300);
            // otpService.generateAndSend receives the saved user (with id)
            when(otpService.generateAndSend(any(User.class), eq(OtpPurpose.REGISTRATION)))
                    .thenReturn(expiry);

            RegisterRequest req = new RegisterRequest(PHONE, "Kamal Perera",
                    "199012345678", PIN, "CUSTOMER");

            RegisterResponse resp = authService.register(req, IP);

            // userId comes from the object returned by userRepository.save()
            assertThat(resp.userId()).isNotNull().isEqualTo(1L);
            assertThat(resp.otpExpiresAt()).isEqualTo(expiry);
            verify(auditService).log(any(), eq("REGISTER_INITIATED"), eq("USER"), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("duplicate phone → ConflictException PHONE_ALREADY_REGISTERED")
        void duplicatePhone() {
            when(userRepository.existsByPhone(PHONE)).thenReturn(true);

            RegisterRequest req = new RegisterRequest(PHONE, "Kamal", "199012345678", PIN, null);

            assertThatThrownBy(() -> authService.register(req, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("PHONE_ALREADY_REGISTERED");
        }

        @Test
        @DisplayName("duplicate NIC → ConflictException NIC_ALREADY_REGISTERED")
        void duplicateNic() {
            when(userRepository.existsByPhone(PHONE)).thenReturn(false);
            when(userRepository.existsByNicNumber("199012345678")).thenReturn(true);

            RegisterRequest req = new RegisterRequest(PHONE, "Kamal", "199012345678", PIN, null);

            assertThatThrownBy(() -> authService.register(req, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("NIC_ALREADY_REGISTERED");
        }

        @Test
        @DisplayName("MERCHANT role is preserved")
        void merchantRole() {
            when(userRepository.existsByPhone(any())).thenReturn(false);
            when(userRepository.existsByNicNumber(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn(HASHED_PIN);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            User saved = pendingUser();
            saved.setRole(UserRole.MERCHANT);
            when(userRepository.save(captor.capture())).thenReturn(saved);
            when(otpService.generateAndSend(any(), any())).thenReturn(Instant.now());

            RegisterRequest req = new RegisterRequest(PHONE, "Kamal", "199012345678", PIN, "MERCHANT");
            authService.register(req, IP);

            assertThat(captor.getValue().getRole()).isEqualTo(UserRole.MERCHANT);
        }
    }

    // ── Login ──────────────────────────────────────────────────

    @Nested @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("success – resets attempts, issues tokens")
        void success() {
            User user = activeUser();
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PIN, HASHED_PIN)).thenReturn(true);
            when(jwtService.generateToken(1L, "CUSTOMER")).thenReturn("access-jwt");
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            TokenResponse resp = authService.login(PHONE, PIN, IP);

            assertThat(resp.accessToken()).isEqualTo("access-jwt");
            assertThat(resp.refreshToken()).isNotBlank();
            assertThat(resp.expiresIn()).isEqualTo(30 * 60L);

            verify(auditService).log(eq(1L), eq("LOGIN_SUCCESS"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("phone not found → InvalidCredentialsException (no phone enumeration)")
        void phoneNotFound() {
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(PHONE, PIN, IP))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(auditService).log(isNull(), eq("LOGIN_FAILURE"), any(), isNull(), any(), eq(IP));
        }

        @Test
        @DisplayName("wrong PIN → InvalidCredentialsException, increments attempts")
        void wrongPin() {
            User user = activeUser();
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PIN, HASHED_PIN)).thenReturn(false);
            when(userRepository.save(any())).thenReturn(user);

            assertThatThrownBy(() -> authService.login(PHONE, PIN, IP))
                    .isInstanceOf(InvalidCredentialsException.class);

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("5th wrong PIN → AccountLockedException, account locked")
        void lockoutOnFifthFailure() {
            User user = activeUser();
            // Simulate 4 prior failures
            for (int i = 0; i < 4; i++) user.incrementFailedAttempts();

            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PIN, HASHED_PIN)).thenReturn(false);
            when(userRepository.save(any())).thenReturn(user);

            assertThatThrownBy(() -> authService.login(PHONE, PIN, IP))
                    .isInstanceOf(AccountLockedException.class);

            assertThat(user.isLockedOut()).isTrue();
            verify(auditService).log(eq(1L), eq("LOGIN_LOCKED"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("already locked → AccountLockedException without checking PIN")
        void alreadyLocked() {
            User user = activeUser();
            user.lockUntil(Instant.now().plusSeconds(600));

            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(PHONE, PIN, IP))
                    .isInstanceOf(AccountLockedException.class);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("frozen account → AuthorizationException ACCOUNT_FROZEN")
        void frozenAccount() {
            User user = activeUser();
            user.setStatus(UserStatus.FROZEN);

            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(PHONE, PIN, IP))
                    .isInstanceOf(AuthorizationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_FROZEN");
        }

        @Test
        @DisplayName("unverified account → AuthorizationException ACCOUNT_NOT_VERIFIED")
        void unverifiedAccount() {
            User user = pendingUser();
            when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(PHONE, PIN, IP))
                    .isInstanceOf(AuthorizationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_NOT_VERIFIED");
        }
    }

    // ── Refresh token ──────────────────────────────────────────

    @Nested @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("valid token → revokes old, issues new access token")
        void success() {
            User user = activeUser();
            String rawToken = "valid-raw-refresh-token";
            String hash     = AuthService.sha256(rawToken);

            RefreshToken stored = new RefreshToken(user, hash,
                    Instant.now().plusSeconds(86400));

            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(jwtService.generateToken(1L, "CUSTOMER")).thenReturn("new-jwt");

            TokenResponse resp = authService.refresh(rawToken, IP);

            assertThat(resp.accessToken()).isEqualTo("new-jwt");
            assertThat(stored.isRevoked()).isTrue();
            verify(auditService).log(eq(1L), eq("TOKEN_REFRESHED"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("expired token → TokenExpiredException")
        void expired() {
            User user = activeUser();
            String rawToken = "expired-token";
            String hash     = AuthService.sha256(rawToken);

            RefreshToken stored = new RefreshToken(user, hash,
                    Instant.now().minusSeconds(1)); // already expired

            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> authService.refresh(rawToken, IP))
                    .isInstanceOf(TokenExpiredException.class);
        }

        @Test
        @DisplayName("revoked token → revokes ALL user tokens (reuse attack)")
        void revokedTokenRevokesAll() {
            User user = activeUser();
            String rawToken = "already-revoked";
            String hash     = AuthService.sha256(rawToken);

            RefreshToken stored = new RefreshToken(user, hash,
                    Instant.now().plusSeconds(86400));
            stored.revoke();

            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> authService.refresh(rawToken, IP))
                    .isInstanceOf(TokenExpiredException.class);

            verify(refreshTokenRepository).revokeAllForUser(1L);
            verify(auditService).log(eq(1L), eq("TOKEN_REUSE_DETECTED"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("unknown token → TokenExpiredException")
        void unknownToken() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("unknown", IP))
                    .isInstanceOf(TokenExpiredException.class);
        }
    }

    // ── Logout ─────────────────────────────────────────────────

    @Nested @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("valid token → revokes it and logs LOGOUT")
        void success() {
            User user = activeUser();
            String rawToken = "my-refresh-token";
            String hash     = AuthService.sha256(rawToken);

            RefreshToken stored = new RefreshToken(user, hash,
                    Instant.now().plusSeconds(86400));
            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            authService.logout(rawToken, 1L, IP);

            assertThat(stored.isRevoked()).isTrue();
            verify(auditService).log(eq(1L), eq("LOGOUT"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("null token → silently succeeds (idempotent)")
        void nullToken() {
            assertThatCode(() -> authService.logout(null, 1L, IP)).doesNotThrowAnyException();
            verify(refreshTokenRepository, never()).findByTokenHash(any());
            verify(auditService).log(eq(1L), eq("LOGOUT"), any(), any(), any(), eq(IP));
        }
    }

    // ── Change PIN ─────────────────────────────────────────────

    @Nested @DisplayName("changePin()")
    class ChangePin {

        @Test
        @DisplayName("success – updates hash, revokes all sessions")
        void success() {
            User user = activeUser();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("123456", HASHED_PIN)).thenReturn(true);  // current OK
            when(passwordEncoder.matches("654321", HASHED_PIN)).thenReturn(false); // new is different
            when(passwordEncoder.encode("654321")).thenReturn("$2a$12$newhash");
            when(userRepository.save(any())).thenReturn(user);

            authService.changePin(1L, "123456", "654321", IP);

            assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
            verify(refreshTokenRepository).revokeAllForUser(1L);
            verify(auditService).log(eq(1L), eq("CHANGE_PIN_SUCCESS"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("wrong current PIN → InvalidCredentialsException")
        void wrongCurrentPin() {
            User user = activeUser();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpin", HASHED_PIN)).thenReturn(false);

            assertThatThrownBy(() -> authService.changePin(1L, "wrongpin", "654321", IP))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(auditService).log(eq(1L), eq("CHANGE_PIN_FAILURE"), any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("new PIN same as current → ValidationException PIN_SAME_AS_CURRENT")
        void samePin() {
            User user = activeUser();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PIN, HASHED_PIN)).thenReturn(true); // current OK
            when(passwordEncoder.matches(PIN, HASHED_PIN)).thenReturn(true); // new also matches

            assertThatThrownBy(() -> authService.changePin(1L, PIN, PIN, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("PIN_SAME_AS_CURRENT");
        }
    }

    // ── sha256 helper ──────────────────────────────────────────

    @Test
    @DisplayName("sha256() – same input always produces same output")
    void sha256Deterministic() {
        String h1 = AuthService.sha256("hello");
        String h2 = AuthService.sha256("hello");
        assertThat(h1).isEqualTo(h2).isNotBlank();
    }

    @Test
    @DisplayName("sha256() – different inputs produce different hashes")
    void sha256Collision() {
        assertThat(AuthService.sha256("abc")).isNotEqualTo(AuthService.sha256("xyz"));
    }
}
