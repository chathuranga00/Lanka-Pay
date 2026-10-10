package com.lankapay.qr.repository;

import com.lankapay.qr.domain.PaymentRequest;
import com.lankapay.qr.domain.PaymentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    Optional<PaymentRequest> findByReference(String reference);

    List<PaymentRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<PaymentRequest> findByPayerIdOrderByCreatedAtDesc(Long payerId);

    @Modifying
    @Query("UPDATE PaymentRequest p SET p.status = 'EXPIRED' WHERE p.status = 'PENDING' AND p.expiresAt < :now")
    int expireOlderThan(@Param("now") Instant now);
}
