package com.lankapay.transfer.repository;

import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {

    Optional<Transfer> findByReference(String reference);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    /** Sender history – paginated. */
    Page<Transfer> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    /** Receiver history – paginated. */
    Page<Transfer> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    /** Object-level check: sender or receiver must be the requesting user. */
    Optional<Transfer> findByIdAndSenderIdOrIdAndReceiverId(
            Long id1, Long senderId, Long id2, Long receiverId);

    @Query("SELECT t FROM Transfer t WHERE t.id = :id AND (t.sender.id = :userId OR t.receiver.id = :userId)")
    Optional<Transfer> findByIdAndUserAccess(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT t FROM Transfer t WHERE (t.sender.id = :userId OR t.receiver.id = :userId) ORDER BY t.createdAt DESC")
    Page<Transfer> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    boolean existsByReference(String reference);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Search by status. */
    Page<Transfer> findByStatus(TransactionStatus status, Pageable pageable);

    List<Transfer> findByStatusIn(Collection<TransactionStatus> statuses);

    long countBySenderIdAndCreatedAtAfter(Long senderId, Instant since);

    @Query("SELECT t FROM Transfer t WHERE " +
           "(:userId IS NULL OR t.sender.id = :userId OR t.receiver.id = :userId) AND " +
           "(:senderId IS NULL OR t.sender.id = :senderId) AND " +
           "(:receiverId IS NULL OR t.receiver.id = :receiverId) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:fromTime IS NULL OR t.createdAt >= :fromTime) AND " +
           "(:toTime IS NULL OR t.createdAt <= :toTime)")
    Page<Transfer> findWithFilters(
            @Param("userId") Long userId,
            @Param("senderId") Long senderId,
            @Param("receiverId") Long receiverId,
            @Param("status") TransactionStatus status,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);
}
