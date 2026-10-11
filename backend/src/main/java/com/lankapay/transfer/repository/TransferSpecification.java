package com.lankapay.transfer.repository;

import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.web.dto.TransferDirection;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransferSpecification {

    public static Specification<Transfer> withFilters(
            Long userId,
            TransferDirection direction,
            TransactionStatus status,
            Instant fromTime,
            Instant toTime,
            String bank,
            BigDecimal minAmount,
            BigDecimal maxAmount) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. User access & Direction
            if (direction == TransferDirection.SENT) {
                predicates.add(cb.equal(root.get("sender").get("id"), userId));
            } else if (direction == TransferDirection.RECEIVED) {
                predicates.add(cb.equal(root.get("receiver").get("id"), userId));
            } else {
                predicates.add(cb.or(
                        cb.equal(root.get("sender").get("id"), userId),
                        cb.equal(root.get("receiver").get("id"), userId)
                ));
            }

            // 2. Status
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 3. Date range
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toTime));
            }

            // 4. Bank (sender or receiver bank matches)
            if (bank != null && !bank.isBlank()) {
                String pattern = "%" + bank.toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("senderBankCode")), pattern),
                        cb.like(cb.lower(root.get("receiverBankCode")), pattern)
                ));
            }

            // 5. Amount range
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
