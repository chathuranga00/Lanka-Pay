package com.lankapay.identity.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AccountLockedException;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.TokenExpiredException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.OtpPurpose;
import com.lankapay.identity.domain.RefreshTokenRecord;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.security.JwtService;
import com.lankapay.identity.web.RegisterRequest;
import com.lankapay.identity.web.RegisterResponse;
import com.lankapay.identity.web.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Core authentication service.
 *
 * Security rules enforced here:
 * - Passwords/PINs are NEVER logged.
 * - Tokens are NEVER logged.
 * - Phone numbers are only logged in masked form.
 * - Login failures increment the attempt counter and may lock the account.
 * - Refresh tokens rotate on every use (old token is revoked, new one issued).
 * - The raw refresh token is returned to the client once and never stored;
 *   only its SHA-256 hash is persisted.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserStore            userStore;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final OtpService           otpService;
    private final AuditService         auditService;

    private final int  loginMaxAttempts;
    private final long loginLockoutMinutes;
    private final long refreshTokenExpiryDays;
    private final long jwtExpiryMinutes;

    public AuthService(
            UserStore userStore,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OtpService otpService,
            AuditService auditService,
            @Value("${app.login.max-attempts:5}")        int loginMaxAttempts,
            @Value("${app.login.lockout-minutes:15}")    long loginLockoutMinutes,
            @Value("${app.refresh-token.expiry-days:7}") long refreshTokenExpiryDays,
            @Value("${app.jwt.expiry-minutes:30}")       long jwtExpiryMinutes) {

        this.userStore               = userStore;
        this.passwordEncoder         = passwordEncoder;
        this.jwtService              = jwtService;
        this.otpService              = otpService;
        this.auditService            = auditService;
        this.loginMaxAttempts        = loginMaxAttempts;
        this.loginLockoutMinutes     = loginLockoutMinutes;
        this.refreshTokenExpiryDays  = refreshTokenExpiryDays;
        this.jwtExpiryMinutes        = jwtExpiryMinutes;
    }

    // ── Register ───────────────────────────────────────────────

    /**
     * Create a new user in PENDING_VERIFICATION state and send an OTP.
     * No tokens are returned yet – the user must verify the OTP first.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request, String ipAddress) {
        Optional<UserAccount> existingPhoneUser = userStore.findUserByPhone(request.phone());
        if (existingPhoneUser.isPresent()) {
            UserAccount existing = existingPhoneUser.get();
            if (existing.status() != UserStatus.PENDING_VERIFICATION) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED",
                        "An account with this phone number already exists");
            }
        }
        Optional<UserAccount> existingNicUser = userStore.findUserByNicNumber(request.nic());
        if (existingNicUser.isPresent()) {
            UserAccount existing = existingNicUser.get();
            if (existing.status() != UserStatus.PENDING_VERIFICATION || !existing.phone().equals(request.phone())) {
                throw new ConflictException("NIC_ALREADY_REGISTERED",
                        "An account with this NIC number already exists");
            }
        }

        Long existingId = existingPhoneUser.map(UserAccount::id).orElse(null);
        String hashedPin = passwordEncoder.encode(request.pin());
        UserRole role = request.roleOrDefault().equals("MERCHANT")
                ? UserRole.MERCHANT : UserRole.CUSTOMER;

        UserAccount user = new UserAccount(
                existingId,
                request.phone(),
                request.fullName(),
                request.nic(),
                hashedPin,
                UserStatus.PENDING_VERIFICATION,
                role,
                0,
                null,
                Instant.now(),
                Instant.now()
        );

        UserAccount saved = userStore.saveUser(user);

        // Create temporary User entity for OTP service signature compatibility
        User tempUser = new User(saved.phone(), saved.fullName(), saved.nicNumber(), saved.passwordHash());
        tempUser.setId(saved.id());
        tempUser.setRole(saved.role());
        tempUser.setStatus(saved.status());

        Instant otpExpiresAt = otpService.generateAndSend(tempUser, OtpPurpose.REGISTRATION);

        auditService.log(saved.id(), "REGISTER_INITIATED", "USER",
                saved.id(), "{\"role\":\"" + role + "\"}", ipAddress);

        log.info("Registration initiated userId={} role={}", saved.id(), role);
        return new RegisterResponse(saved.id(), otpExpiresAt);
    }

    // ── Verify OTP (activates account after registration) ──────

    /**
     * Verify the registration OTP. Activates the account and issues tokens.
     */
    @Transactional
    public TokenResponse verifyOtp(Long userId, String otp, String ipAddress) {
        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ValidationException("USER_NOT_FOUND", "User not found"));

        if (user.status() != UserStatus.PENDING_VERIFICATION) {
            throw new ValidationException("ALREADY_VERIFIED", "Account is already active");
        }

        User tempUser = new User(user.phone(), user.fullName(), user.nicNumber(), user.passwordHash());
        tempUser.setId(user.id());
        tempUser.setRole(user.role());
        tempUser.setStatus(user.status());

        otpService.verify(tempUser, OtpPurpose.REGISTRATION, otp);

        UserAccount updated = user.withStatus(UserStatus.ACTIVE);
        userStore.saveUser(updated);

        TokenResponse tokens = issueTokenPair(updated);

        auditService.log(user.id(), "REGISTER_COMPLETED", "USER",
                user.id(), null, ipAddress);

        log.info("Account activated userId={}", user.id());
        return tokens;
    }

    // ── Login ──────────────────────────────────────────────────

    /**
     * Authenticate with phone + PIN.
     * Enforces lockout after {@code loginMaxAttempts} consecutive failures.
     */
    @Transactional
    public TokenResponse login(String phone, String pin, String ipAddress) {
        UserAccount user = userStore.findUserByPhone(phone).orElse(null);

        if (user == null) {
            auditService.log(null, "LOGIN_FAILURE", "USER", null,
                    "{\"reason\":\"phone_not_found\"}", ipAddress);
            throw new InvalidCredentialsException();
        }

        if (user.isLocked()) {
            auditService.log(user.id(), "LOGIN_BLOCKED", "USER",
                    user.id(), "{\"reason\":\"account_locked\"}", ipAddress);
            throw new AccountLockedException(user.lockedUntil());
        }

        if (user.status() == UserStatus.FROZEN) {
            auditService.log(user.id(), "LOGIN_FAILURE", "USER",
                    user.id(), "{\"reason\":\"account_frozen\"}", ipAddress);
            throw new AuthorizationException("ACCOUNT_FROZEN",
                    "Your account has been frozen. Contact support.");
        }

        if (user.status() == UserStatus.PENDING_VERIFICATION) {
            auditService.log(user.id(), "LOGIN_FAILURE", "USER",
                    user.id(), "{\"reason\":\"not_verified\"}", ipAddress);
            throw new AuthorizationException("ACCOUNT_NOT_VERIFIED",
                    "Please verify your phone number before logging in.");
        }

        if (!passwordEncoder.matches(pin, user.passwordHash())) {
            Instant lockUntil = Instant.now().plus(loginLockoutMinutes, ChronoUnit.MINUTES);
            UserAccount updated = userStore.incrementFailedLoginAttempts(user.id(), loginMaxAttempts, lockUntil);
            if (updated.isLocked()) {
                auditService.log(user.id(), "LOGIN_LOCKED", "USER",
                        user.id(),
                        "{\"attempts\":" + updated.failedLoginAttempts() + "}",
                        ipAddress);
                throw new AccountLockedException(updated.lockedUntil());
            }
            auditService.log(user.id(), "LOGIN_FAILURE", "USER",
                    user.id(),
                    "{\"attempts\":" + updated.failedLoginAttempts() + "}",
                    ipAddress);
            throw new InvalidCredentialsException();
        }

        // Success — reset counter transactionally and issue tokens
        userStore.resetFailedLoginAttempts(user.id());
        UserAccount reset = user.withFailedLoginAttempts(0).withLockedUntil(null);

        TokenResponse tokens = issueTokenPair(reset);

        auditService.log(user.id(), "LOGIN_SUCCESS", "USER",
                user.id(), null, ipAddress);
        log.info("Login success userId={}", user.id());
        return tokens;
    }

    // ── Refresh token ──────────────────────────────────────────

    /**
     * Rotate a refresh token: validate the old one, revoke it, issue a new pair.
     * This implements refresh-token rotation to limit the impact of token theft.
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String ipAddress) {
        String tokenHash = sha256(rawRefreshToken);

        var stored = userStore.findRefreshTokenByHash(tokenHash)
                .orElseThrow(TokenExpiredException::new);

        if (!stored.isValid()) {
            userStore.revokeAllRefreshTokensForUser(stored.userId());
            auditService.log(stored.userId(), "TOKEN_REUSE_DETECTED", "USER",
                    stored.userId(), null, ipAddress);
            throw new TokenExpiredException();
        }

        userStore.saveRefreshToken(stored.revoke());

        UserAccount user = userStore.findUserById(stored.userId())
                .orElseThrow(TokenExpiredException::new);
        TokenResponse tokens = issueTokenPair(user);

        auditService.log(user.id(), "TOKEN_REFRESHED", "USER",
                user.id(), null, ipAddress);
        return tokens;
    }

    // ── Logout ─────────────────────────────────────────────────

    /**
     * Revoke the provided refresh token (single-device logout).
     * If no matching token is found, silently succeed (idempotent).
     */
    @Transactional
    public void logout(String rawRefreshToken, Long actorId, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = sha256(rawRefreshToken);
        userStore.findRefreshTokenByHash(tokenHash).ifPresent(t -> {
            userStore.saveRefreshToken(t.revoke());
        });
        auditService.log(actorId, "LOGOUT", "USER", actorId, null, ipAddress);
        log.info("Logout userId={}", actorId);
    }

    // ── Change PIN ─────────────────────────────────────────────

    /**
     * Change a user's PIN.
     * Verifies the current PIN before accepting the new one.
     * Revokes all refresh tokens on success (forces re-login on all devices).
     */
    @Transactional
    public void changePin(Long userId, String currentPin, String newPin, String ipAddress) {
        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ValidationException("USER_NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(currentPin, user.passwordHash())) {
            auditService.log(userId, "CHANGE_PIN_FAILURE", "USER", userId,
                    "{\"reason\":\"wrong_current_pin\"}", ipAddress);
            throw new InvalidCredentialsException();
        }

        if (passwordEncoder.matches(newPin, user.passwordHash())) {
            throw new ValidationException("PIN_SAME_AS_CURRENT",
                    "New PIN must be different from the current PIN");
        }

        userStore.saveUser(user.withPasswordHash(passwordEncoder.encode(newPin)));
        userStore.revokeAllRefreshTokensForUser(userId);

        auditService.log(userId, "CHANGE_PIN_SUCCESS", "USER", userId, null, ipAddress);
        log.info("PIN changed userId={}", userId);
    }

    // ── Internal helpers ───────────────────────────────────────

    /**
     * Issue a JWT access token + a new rotating refresh token.
     * The raw refresh token is returned to the caller once only.
     * Only its SHA-256 hash is stored in the database.
     */
    private TokenResponse issueTokenPair(UserAccount user) {
        String accessToken  = jwtService.generateToken(user.id(), user.role().name());
        String rawRefresh   = generateRawRefreshToken();
        String refreshHash  = sha256(rawRefresh);
        Instant refreshExpiry = Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS);

        userStore.saveRefreshToken(new RefreshTokenRecord(user.id(), refreshHash, refreshExpiry));

        long expiresIn = jwtExpiryMinutes * 60;
        return TokenResponse.full(accessToken, rawRefresh, expiresIn);
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hash of a string, Base64-URL encoded.
     * Used to hash refresh tokens before storage.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void deleteUserByPhone(String phone) {
        userStore.deleteUserByPhone(phone);
    }
}
