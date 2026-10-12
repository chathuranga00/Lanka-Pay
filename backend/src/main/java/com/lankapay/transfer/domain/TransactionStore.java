package com.lankapay.transfer.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain port interface for transfer, transaction events, daily limits, and idempotency persistence.
 */
public interface TransactionStore {

    // ── Transfers ──────────────────────────────────────────

    Optional<TransactionRecord> findTransferById(Long id);

    Optional<TransactionRecord> findTransferByReference(String reference);

    Optional<TransactionRecord> findTransferByIdempotencyKey(String idempotencyKey);

    boolean existsTransferByReference(String reference);

    boolean existsTransferByIdempotencyKey(String idempotencyKey);

    TransactionRecord saveTransfer(TransactionRecord transfer);

    Page<TransactionRecord> findTransfersBySenderId(Long senderId, Pageable pageable);

    Page<TransactionRecord> findTransfersByReceiverId(Long receiverId, Pageable pageable);

    Page<TransactionRecord> findTransfersByUserId(Long userId, Pageable pageable);

    Page<TransactionRecord> findTransfersByStatus(TransactionStatus status, Pageable pageable);

    List<TransactionRecord> findTransfersByStatusIn(Collection<TransactionStatus> statuses);

    long countTransfersBySenderIdAndCreatedAtAfter(Long senderId, Instant since);

    Page<TransactionRecord> searchTransfers(
            Long userId,
            com.lankapay.transfer.web.dto.TransferDirection direction,
            TransactionStatus status,
            Instant fromTime,
            Instant toTime,
            String bankCode,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable
    );

    // ── Transaction Events ────────────────────────────────

    TransactionEventRecord saveTransactionEvent(TransactionEventRecord event);

    List<TransactionEventRecord> findEventsByTransferId(Long transferId);

    List<TransactionEventRecord> findEventsByToStatus(TransactionStatus toStatus);

    // ── Daily Limits ──────────────────────────────────────

    Optional<DailyLimitRecord> findDailyLimit(Long userId, LocalDate date);

    DailyLimitRecord saveDailyLimit(DailyLimitRecord dailyLimit);

    // ── Idempotency Records ───────────────────────────────

    Optional<IdempotencyRecordModel> findIdempotencyRecord(String key);

    boolean existsIdempotencyRecord(String key);

    IdempotencyRecordModel saveIdempotencyRecord(IdempotencyRecordModel record);

    // ── Atomic Multi-Document Transactions ────────────────
    TransactionRecord createTransferWithIdempotency(
            TransactionRecord transfer,
            String idempotencyKey,
            String payloadHash
    );

    TransactionRecord transitionTransferStatus(
            Long transferId,
            TransactionStatus targetStatus,
            String reason,
            String detailJson,
            String bankTxRef,
            String providerRef,
            String failureReason,
            Long expectedVersion
    );

    long getCardDailySpentMinor(Long cardId, LocalDate date);

    TransactionRecord updateTransferChallenge(Long transferId, String challengeRef, int attempts);
}
