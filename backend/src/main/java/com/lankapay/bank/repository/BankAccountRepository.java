package com.lankapay.bank.repository;

import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByUserIdAndStatus(Long userId, BankAccountStatus status);

    List<BankAccount> findByUserIdOrderByLinkedAtDesc(Long userId);

    Optional<BankAccount> findByAccountNumberHash(String accountNumberHash);

    /** Object-level check: account must belong to the requesting user. */
    Optional<BankAccount> findByIdAndUserId(Long id, Long userId);

    /** Find the active default for a user (at most one should exist). */
    Optional<BankAccount> findByUserIdAndIsDefaultTrueAndStatus(
            Long userId, BankAccountStatus status);

    /** Atomically clear the default flag for all accounts of a user.
     *  Must be called within the same transaction before setting a new default. */
    @Modifying
    @Query("UPDATE BankAccount ba SET ba.isDefault = false WHERE ba.user.id = :userId")
    int clearDefaultForUser(Long userId);

    /** Count active accounts for a user (used to enforce minimum-one rule on remove). */
    long countByUserIdAndStatus(Long userId, BankAccountStatus status);
}
