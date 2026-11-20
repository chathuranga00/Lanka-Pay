package com.lankapay.bill.domain;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for BillPaymentRecord entities.
 */
public interface BillPaymentStore {

    BillPaymentRecord save(BillPaymentRecord record);

    Optional<BillPaymentRecord> findById(Long id);

    Optional<BillPaymentRecord> findByPaymentReference(String paymentReference);

    Optional<BillPaymentRecord> findByIdempotencyKey(String idempotencyKey);

    List<BillPaymentRecord> findByUserId(Long userId, int offset, int limit);

    int countByUserId(Long userId);
}
