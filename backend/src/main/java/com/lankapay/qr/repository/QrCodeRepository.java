package com.lankapay.qr.repository;

import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {

    Optional<QrCode> findByReference(String reference);

    Optional<QrCode> findByQrId(String qrId);

    @Query("SELECT q FROM QrCode q WHERE q.reference = :ref OR q.qrId = :ref")
    Optional<QrCode> findByReferenceOrQrId(@Param("ref") String ref);

    Optional<QrCode> findByReferenceAndStatus(String reference, QrStatus status);

    /** Scheduled job: expire all ACTIVE QR codes whose TTL has elapsed. */
    @Modifying
    @Query("UPDATE QrCode q SET q.status = 'EXPIRED' WHERE q.status = 'ACTIVE' AND q.expiresAt IS NOT NULL AND q.expiresAt < :now")
    int expireOlderThan(@Param("now") Instant now);

    /** Merchant's QR code history. */
    List<QrCode> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
