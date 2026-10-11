package com.lankapay.admin.repository;

import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AdminTransferSpecification {

    public static Specification<Transfer> withFilters(Long userId,
                                                        Long senderId,
                                                        Long receiverId,
                                                        TransactionStatus status,
                                                        String bankCode,
                                                        BigDecimal minAmount,
                                                        BigDecimal maxAmount,
                                                        String reference,
                                                        Instant from,
                                                        Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("sender").get("id"), userId),
                        cb.equal(root.get("receiver").get("id"), userId)
                ));
            }

            if (senderId != null) {
                predicates.add(cb.equal(root.get("sender").get("id"), senderId));
            }

            if (receiverId != null) {
                predicates.add(cb.equal(root.get("receiver").get("id"), receiverId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (bankCode != null && !bankCode.isBlank()) {
                String trimmed = bankCode.trim();
                predicates.add(cb.or(
                        cb.equal(root.get("senderBankCode"), trimmed),
                        cb.equal(root.get("receiverBankCode"), trimmed)
                ));
            }

            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }

            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            if (reference != null && !reference.isBlank()) {
                predicates.add(cb.like(root.get("reference"), "%" + reference.trim() + "%"));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
