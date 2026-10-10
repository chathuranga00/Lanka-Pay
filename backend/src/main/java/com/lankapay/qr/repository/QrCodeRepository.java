package com.lankapay.qr.repository;

import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {

    Optional<QrCode> findByQrId(String qrId);

    Optional<QrCode> findByQrIdAndStatus(String qrId, QrStatus status);

    /** Scheduled job: expire all ACTIVE QR codes whose TTL has elapsed. */
    @Modifying
    @Query("UPDATE QrCode q SET q.status = 'EXPIRED' WHERE q.status = 'ACTIVE' AND q.expiresAt < :now")
    int expireOlderThan(Instant now);

    /** Merchant's QR code history. */
    List<QrCode> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
