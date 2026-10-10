package com.lankapay.transfer.repository;

import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionEventRepository extends JpaRepository<TransactionEvent, Long> {

    List<TransactionEvent> findByTransferIdOrderByOccurredAtAsc(Long transferId);

    List<TransactionEvent> findByToStatus(TransactionStatus toStatus);
}
