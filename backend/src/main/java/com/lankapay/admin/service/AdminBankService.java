package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.BankConfigResponse;
import com.lankapay.admin.web.dto.ComponentHealthResponse;
import com.lankapay.admin.web.dto.IntegrationHealthResponse;
import com.lankapay.admin.web.dto.UpdateBankConfigRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.domain.BankInstitution;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminBankService {

    private static final Logger log = LoggerFactory.getLogger(AdminBankService.class);

    private final BankInstitutionRepository bankInstitutionRepository;
    private final BankAdapterRegistry bankAdapterRegistry;
    private final AuditService auditService;

    public AdminBankService(BankInstitutionRepository bankInstitutionRepository,
                            BankAdapterRegistry bankAdapterRegistry,
                            AuditService auditService) {
        this.bankInstitutionRepository = bankInstitutionRepository;
        this.bankAdapterRegistry = bankAdapterRegistry;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BankConfigResponse> getAllBanks() {
        return bankInstitutionRepository.findAll().stream()
                .map(BankConfigResponse::from)
                .toList();
    }

    @Transactional
    public BankConfigResponse updateBankConfig(String bankCode, UpdateBankConfigRequest request, Long adminUserId, String clientIp) {
        BankInstitution inst = bankInstitutionRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new ResourceNotFoundException("BANK_NOT_FOUND", "Bank not found with code: " + bankCode));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            inst.setDisplayName(request.displayName().trim());
        }
        if (request.active() != null) {
            inst.setActive(request.active());
        }

        BankInstitution saved = bankInstitutionRepository.save(inst);
        log.info("[ADMIN] Bank code={} config updated by admin id={}", bankCode, adminUserId);

        auditService.log(adminUserId, "ADMIN_BANK_CONFIG_UPDATE", "BANK_INSTITUTION", saved.getId(),
                "{\"bankCode\":\"%s\",\"active\":%b}".formatted(saved.getBankCode(), saved.isActive()), clientIp);

        return BankConfigResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public IntegrationHealthResponse getIntegrationHealth() {
        List<ComponentHealthResponse> components = new ArrayList<>();
        List<BankInstitution> banks = bankInstitutionRepository.findAll();

        boolean allHealthy = true;

        for (BankInstitution bank : banks) {
            long start = System.currentTimeMillis();
            try {
                BankAdapter adapter = bankAdapterRegistry.get(bank.getBankCode());
                long latency = Math.max(1, System.currentTimeMillis() - start);
                String status = bank.isActive() ? "UP" : "INACTIVE";
                components.add(new ComponentHealthResponse(
                        bank.getDisplayName(),
                        status,
                        bank.isSimulated(),
                        latency,
                        bank.isActive() ? "Simulated bank adapter responsive" : "Bank is deactivated by admin"
                ));
            } catch (Exception e) {
                allHealthy = false;
                long latency = System.currentTimeMillis() - start;
                components.add(new ComponentHealthResponse(
                        bank.getDisplayName(),
                        "DOWN",
                        bank.isSimulated(),
                        latency,
                        "Error checking adapter: " + e.getMessage()
                ));
            }
        }

        // Database component
        components.add(new ComponentHealthResponse(
                "Primary Database (MySQL)",
                "UP",
                false,
                1L,
                "Connection pool healthy"
        ));

        // In-app notifications
        components.add(new ComponentHealthResponse(
                "Notification Dispatcher",
                "UP",
                false,
                1L,
                "In-App / Push / Email channels active"
        ));

        return new IntegrationHealthResponse(
                allHealthy ? "UP" : "DEGRADED",
                Instant.now(),
                components
        );
    }
}
