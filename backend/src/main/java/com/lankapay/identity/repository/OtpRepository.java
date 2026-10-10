package com.lankapay.identity.repository;

import com.lankapay.identity.domain.Otp;
import com.lankapay.identity.domain.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    /** Find the most recent unused, unexpired OTP for a user+purpose. */
    @Query("""
           SELECT o FROM Otp o
           WHERE o.user.id = :userId
             AND o.purpose = :purpose
             AND o.used = false
             AND o.expiresAt > :now
           ORDER BY o.createdAt DESC
           LIMIT 1
           """)
    Optional<Otp> findLatestValid(Long userId, OtpPurpose purpose, Instant now);

    /** Scheduled cleanup: delete expired OTPs to keep the table small. */
    @Modifying
    @Query("DELETE FROM Otp o WHERE o.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
