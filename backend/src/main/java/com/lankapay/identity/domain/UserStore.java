package com.lankapay.identity.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Storage port interface for user identity, OTP, and session token operations.
 * Decouples services from specific database persistence technologies.
 */
public interface UserStore {

    UserAccount saveUser(UserAccount user);

    Optional<UserAccount> findUserById(Long id);

    Optional<UserAccount> findUserByPhone(String phone);

    Optional<UserAccount> findUserByNicNumber(String nicNumber);

    boolean existsByPhone(String phone);

    boolean existsByNicNumber(String nicNumber);

    Optional<UserAccount> findByPhoneAndStatusNot(String phone, UserStatus status);

    OtpRecord saveOtp(OtpRecord otp);

    Optional<OtpRecord> findLatestValidOtp(Long userId, OtpPurpose purpose, Instant now);

    int deleteExpiredOtpsBefore(Instant cutoff);

    Optional<OtpMetadataRecord> findOtpMetadata(Long userId, OtpPurpose purpose);

    OtpMetadataRecord saveOtpMetadata(OtpMetadataRecord metadata);

    RefreshTokenRecord saveRefreshToken(RefreshTokenRecord token);

    Optional<RefreshTokenRecord> findRefreshTokenByHash(String tokenHash);

    int revokeAllRefreshTokensForUser(Long userId);

    UserAccount incrementFailedLoginAttempts(Long userId, int maxAttempts, Instant lockUntil);

    void resetFailedLoginAttempts(Long userId);

    boolean verifyAndConsumeOtp(Long challengeId, String plainOtp, java.util.function.BiPredicate<String, String> hashMatcher, int maxAttempts);

    org.springframework.data.domain.Page<UserAccount> searchUsers(String phone, String nic, UserStatus status, UserRole role, org.springframework.data.domain.Pageable pageable);

    default void deleteUserByPhone(String phone) {}
}

