package com.lankapay.bank.repository;

import com.lankapay.bank.domain.AccountConsent;
import com.lankapay.bank.domain.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountConsentRepository extends JpaRepository<AccountConsent, Long> {

    List<AccountConsent> findByBankAccountIdOrderByConsentedAtDesc(Long bankAccountId);

    List<AccountConsent> findByUserIdAndConsentTypeOrderByConsentedAtDesc(
            Long userId, ConsentType consentType);
}
