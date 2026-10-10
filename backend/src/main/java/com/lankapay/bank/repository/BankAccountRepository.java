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

    Optional<BankAccount> findByAccountNumberHash(String accountNumberHash);

    /** Clear default flag for all accounts of a user before setting a new default. */
    @Modifying
    @Query("UPDATE BankAccount ba SET ba.isDefault = false WHERE ba.user.id = :userId")
    int clearDefaultForUser(Long userId);

    /** Object-level check: ensure the account belongs to the requesting user. */
    Optional<BankAccount> findByIdAndUserId(Long id, Long userId);
}
