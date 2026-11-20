package com.lankapay.bank.domain;

import java.util.List;
import java.util.Optional;

/**
 * Storage port interface for linked bank account operations.
 * Decouples services from specific database persistence technologies.
 */
public interface LinkedAccountStore {

    LinkedAccount saveAccount(LinkedAccount account);

    Optional<LinkedAccount> findAccountById(Long id);

    Optional<LinkedAccount> findAccountByIdAndUserId(Long id, Long userId);

    Optional<LinkedAccount> findAccountByAccountNumberHash(String accountNumberHash);

    List<LinkedAccount> findAccountsByUserIdAndStatus(Long userId, BankAccountStatus status);

    List<LinkedAccount> findAccountsByUserIdOrderByLinkedAtDesc(Long userId);

    Optional<LinkedAccount> findActiveDefaultAccount(Long userId);

    int clearDefaultAccountForUser(Long userId);

    LinkedAccount setDefaultAccount(Long accountId, Long userId);

    long countActiveAccounts(Long userId);

    AccountConsentRecord saveConsent(AccountConsentRecord consent);

    List<AccountConsentRecord> findConsentsByBankAccountId(Long bankAccountId);

    List<AccountConsentRecord> findConsentsByUserIdAndType(Long userId, ConsentType consentType);

    Optional<BankInstitutionRecord> findActiveInstitutionByBankCode(String bankCode);

    Optional<BankInstitutionRecord> findInstitutionByBankCode(String bankCode);

    List<BankInstitutionRecord> findAllInstitutions();

    BankInstitutionRecord saveInstitution(BankInstitutionRecord institution);
}

