package com.lankapay.bank.repository;

import com.lankapay.bank.domain.BankInstitution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankInstitutionRepository extends JpaRepository<BankInstitution, Long> {

    Optional<BankInstitution> findByBankCodeAndActiveTrue(String bankCode);
}
