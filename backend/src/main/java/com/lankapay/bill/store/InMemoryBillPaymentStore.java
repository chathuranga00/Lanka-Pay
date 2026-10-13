package com.lankapay.bill.store;

import com.lankapay.bill.domain.BillPaymentRecord;
import com.lankapay.bill.domain.BillPaymentStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory thread-safe storage for BillPaymentRecord entities.
 */
@Component
public class InMemoryBillPaymentStore implements BillPaymentStore {

    private final Map<Long, BillPaymentRecord> store = new ConcurrentHashMap<>();
    private final Map<String, Long> refIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> idempotencyIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1000);

    @Override
    public BillPaymentRecord save(BillPaymentRecord record) {
        Long id = record.id() != null ? record.id() : idGen.incrementAndGet();
        Instant now = Instant.now();
        Instant created = record.createdAt() != null ? record.createdAt() : now;
        BillPaymentRecord entity = new BillPaymentRecord(
                id,
                record.paymentReference(),
                record.idempotencyKey(),
                record.userId(),
                record.billerId(),
                record.billerName(),
                record.category(),
                record.customerReferenceEncrypted(),
                record.customerReferenceMasked(),
                record.customerName(),
                record.amount(),
                record.fee(),
                record.totalAmount(),
                record.currency(),
                record.status(),
                record.failureReason(),
                record.billerConfirmationRef(),
                record.fundingType(),
                record.fundingSourceId(),
                record.fundingLabel(),
                record.billDueDate(),
                record.transactionId(),
                created,
                now
        );
        store.put(id, entity);
        if (entity.paymentReference() != null) {
            refIndex.put(entity.paymentReference(), id);
        }
        if (entity.idempotencyKey() != null) {
            idempotencyIndex.put(entity.idempotencyKey(), id);
        }
        return entity;
    }

    @Override
    public Optional<BillPaymentRecord> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<BillPaymentRecord> findByPaymentReference(String paymentReference) {
        if (paymentReference == null) return Optional.empty();
        Long id = refIndex.get(paymentReference);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<BillPaymentRecord> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        Long id = idempotencyIndex.get(idempotencyKey);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<BillPaymentRecord> findByUserId(Long userId, int offset, int limit) {
        if (userId == null) return Collections.emptyList();
        return store.values().stream()
                .filter(b -> userId.equals(b.userId()))
                .sorted(Comparator.comparing(BillPaymentRecord::createdAt).reversed())
                .skip(Math.max(0, offset))
                .limit(limit > 0 ? limit : 20)
                .toList();
    }

    @Override
    public int countByUserId(Long userId) {
        if (userId == null) return 0;
        return (int) store.values().stream().filter(b -> userId.equals(b.userId())).count();
    }

    public void clear() {
        store.clear();
        refIndex.clear();
        idempotencyIndex.clear();
    }
}
