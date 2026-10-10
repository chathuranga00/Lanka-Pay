package com.lankapay.transfer.repository;

import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByReference(String reference);

    /** Sender history – paginated. */
    Page<Transfer> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    /** Receiver history – paginated. */
    Page<Transfer> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    /** Object-level: sender or receiver must be the requesting user. */
    Optional<Transfer> findByIdAndSenderIdOrIdAndReceiverId(
            Long id1, Long senderId, Long id2, Long receiverId);

    boolean existsByReference(String reference);

    /** Admin: full search by status. */
    Page<Transfer> findByStatus(TransferStatus status, Pageable pageable);
}
