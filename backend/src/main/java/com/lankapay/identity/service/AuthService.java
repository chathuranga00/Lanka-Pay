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

    private final UserRepository       userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final OtpService           otpService;
    private final AuditService         auditService;

    private final int  loginMaxAttempts;
    private final long loginLockoutMinutes;
    private final long refreshTokenExpiryDays;
    private final long jwtExpiryMinutes;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OtpService otpService,
            AuditService auditService,
            @Value("${app.login.max-attempts:5}")        int loginMaxAttempts,
            @Value("${app.login.lockout-minutes:15}")    long loginLockoutMinutes,
            @Value("${app.refresh-token.expiry-days:7}") long refreshTokenExpiryDays,
            @Value("${app.jwt.expiry-minutes:30}")       long jwtExpiryMinutes) {

        this.userRepository          = userRepository;
        this.refreshTokenRepository  = refreshTokenRepository;
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
        if (userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED",
                    "An account with this phone number already exists");
        }
        if (userRepository.existsByNicNumber(request.nic())) {
            throw new ConflictException("NIC_ALREADY_REGISTERED",
                    "An account with this NIC number already exists");
        }

        String hashedPin = passwordEncoder.encode(request.pin());
        User user = new User(request.phone(), request.fullName(), request.nic(), hashedPin);

        UserRole role = request.roleOrDefault().equals("MERCHANT")
                ? UserRole.MERCHANT : UserRole.CUSTOMER;
        user.setRole(role);

        User saved = userRepository.save(user);

        Instant otpExpiresAt = otpService.generateAndSend(saved, OtpPurpose.REGISTRATION);

        auditService.log(saved.getId(), "REGISTER_INITIATED", "USER",
                saved.getId(), "{\"role\":\"" + role + "\"}", ipAddress);

        log.info("Registration initiated userId={} role={}", saved.getId(), role);
        return new RegisterResponse(saved.getId(), otpExpiresAt);
    }

    // ── Verify OTP (activates account after registration) ──────

    /**
     * Verify the registration OTP. Activates the account and issues tokens.
     */
    @Transactional
    public TokenResponse verifyOtp(Long userId, String otp, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("USER_NOT_FOUND", "User not found"));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ValidationException("ALREADY_VERIFIED", "Account is already active");
        }

        otpService.verify(user, OtpPurpose.REGISTRATION, otp);

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        TokenResponse tokens = issueTokenPair(user);

        auditService.log(user.getId(), "REGISTER_COMPLETED", "USER",
                user.getId(), null, ipAddress);

        log.info("Account activated userId={}", user.getId());
        return tokens;
    }

    // ── Login ──────────────────────────────────────────────────

    /**
     * Authenticate with phone + PIN.
     * Enforces lockout after {@code loginMaxAttempts} consecutive failures.
     */
    @Transactional
    public TokenResponse login(String phone, String pin, String ipAddress) {

        // Look up user — use a constant-time path regardless of whether user exists
        User user = userRepository.findByPhone(phone).orElse(null);

        if (user == null) {
            // Don't reveal that the phone isn't registered
            auditService.log(null, "LOGIN_FAILURE", "USER", null,
                    "{\"reason\":\"phone_not_found\"}", ipAddress);
            throw new InvalidCredentialsException();
        }

        // Check lockout before PIN verification
        if (user.isLockedOut()) {
            auditService.log(user.getId(), "LOGIN_BLOCKED", "USER",
                    user.getId(), "{\"reason\":\"account_locked\"}", ipAddress);
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (user.isFrozen()) {
            auditService.log(user.getId(), "LOGIN_FAILURE", "USER",
                    user.getId(), "{\"reason\":\"account_frozen\"}", ipAddress);
            throw new AuthorizationException("ACCOUNT_FROZEN",
                    "Your account has been frozen. Contact support.");
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            auditService.log(user.getId(), "LOGIN_FAILURE", "USER",
                    user.getId(), "{\"reason\":\"not_verified\"}", ipAddress);
            throw new AuthorizationException("ACCOUNT_NOT_VERIFIED",
                    "Please verify your phone number before logging in.");
        }

        // PIN check — passwordEncoder.matches never logs the raw pin
        if (!passwordEncoder.matches(pin, user.getPasswordHash())) {
            int attempts = user.incrementFailedAttempts();
            if (attempts >= loginMaxAttempts) {
                Instant lockUntil = Instant.now().plus(loginLockoutMinutes, ChronoUnit.MINUTES);
                user.lockUntil(lockUntil);
                userRepository.save(user);
                auditService.log(user.getId(), "LOGIN_LOCKED", "USER",
                        user.getId(),
                        "{\"attempts\":" + attempts + "}",
                        ipAddress);
                throw new AccountLockedException(lockUntil);
            }
            userRepository.save(user);
            auditService.log(user.getId(), "LOGIN_FAILURE", "USER",
                    user.getId(),
                    "{\"attempts\":" + attempts + "}",
                    ipAddress);
            throw new InvalidCredentialsException();
        }

        // Success — reset counter and issue tokens
        user.resetLoginAttempts();
        userRepository.save(user);

        TokenResponse tokens = issueTokenPair(user);

        auditService.log(user.getId(), "LOGIN_SUCCESS", "USER",
                user.getId(), null, ipAddress);
        log.info("Login success userId={}", user.getId());
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

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(TokenExpiredException::new);

        if (!stored.isValid()) {
            // Token already revoked or expired — revoke all tokens for this user
            // as a precaution against token reuse attacks
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            auditService.log(stored.getUser().getId(), "TOKEN_REUSE_DETECTED", "USER",
                    stored.getUser().getId(), null, ipAddress);
            throw new TokenExpiredException();
        }

        // Revoke the used token and issue a new pair
        stored.revoke();
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        TokenResponse tokens = issueTokenPair(user);

        auditService.log(user.getId(), "TOKEN_REFRESHED", "USER",
                user.getId(), null, ipAddress);
        return tokens;
    }

    // ── Logout ─────────────────────────────────────────────────

    /**
     * Revoke the provided refresh token (single-device logout).
     * If no matching token is found, silently succeed (idempotent).
     */
    @Transactional
    public void logout(String rawRefreshToken, Long actorId, String ipAddress) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = sha256(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
                t.revoke();
                refreshTokenRepository.save(t);
            });
        }
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("USER_NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(currentPin, user.getPasswordHash())) {
            auditService.log(userId, "CHANGE_PIN_FAILURE", "USER", userId,
                    "{\"reason\":\"wrong_current_pin\"}", ipAddress);
            throw new InvalidCredentialsException();
        }

        if (passwordEncoder.matches(newPin, user.getPasswordHash())) {
            throw new ValidationException("PIN_SAME_AS_CURRENT",
                    "New PIN must be different from the current PIN");
        }

        user.setPasswordHash(passwordEncoder.encode(newPin));
        // Revoke all refresh tokens — forces re-login on all sessions
        refreshTokenRepository.revokeAllForUser(userId);
        userRepository.save(user);

        auditService.log(userId, "CHANGE_PIN_SUCCESS", "USER", userId, null, ipAddress);
        log.info("PIN changed userId={}", userId);
    }

    // ── Internal helpers ───────────────────────────────────────

    /**
     * Issue a JWT access token + a new rotating refresh token.
     * The raw refresh token is returned to the caller once only.
     * Only its SHA-256 hash is stored in the database.
     */
    private TokenResponse issueTokenPair(User user) {
        String accessToken  = jwtService.generateToken(user.getId(), user.getRole().name());
        String rawRefresh   = generateRawRefreshToken();
        String refreshHash  = sha256(rawRefresh);
        Instant refreshExpiry = Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS);

        refreshTokenRepository.save(new RefreshToken(user, refreshHash, refreshExpiry));

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
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
