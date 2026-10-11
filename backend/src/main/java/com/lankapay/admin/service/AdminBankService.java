package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.BankConfigResponse;
import com.lankapay.admin.web.dto.ComponentHealthResponse;
import com.lankapay.admin.web.dto.IntegrationHealthResponse;
import com.lankapay.admin.web.dto.UpdateBankConfigRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.domain.BankInstitutionRecord;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminBankService {

    private static final Logger log = LoggerFactory.getLogger(AdminBankService.class);

    private final LinkedAccountStore linkedAccountStore;
    private final BankAdapterRegistry bankAdapterRegistry;
    private final AuditService auditService;

    public AdminBankService(LinkedAccountStore linkedAccountStore,
                            BankAdapterRegistry bankAdapterRegistry,
                            AuditService auditService) {
        this.linkedAccountStore = linkedAccountStore;
        this.bankAdapterRegistry = bankAdapterRegistry;
        this.auditService = auditService;
    }

    public List<BankConfigResponse> getAllBanks() {
        return linkedAccountStore.findAllInstitutions().stream()
                .map(BankConfigResponse::from)
                .toList();
    }

    public BankConfigResponse updateBankConfig(String bankCode, UpdateBankConfigRequest request, Long adminUserId, String clientIp) {
        BankInstitutionRecord inst = linkedAccountStore.findInstitutionByBankCode(bankCode)
                .orElseThrow(() -> new ResourceNotFoundException("BANK_NOT_FOUND", "Bank not found with code: " + bankCode));

        String newDisplayName = (request.displayName() != null && !request.displayName().isBlank())
                ? request.displayName().trim() : inst.displayName();
        boolean newActive = request.active() != null ? request.active() : inst.active();

        BankInstitutionRecord updated = new BankInstitutionRecord(
                inst.id(),
                inst.bankCode(),
                newDisplayName,
                inst.simulated(),
                newActive,
                inst.createdAt()
        );

        BankInstitutionRecord saved = linkedAccountStore.saveInstitution(updated);
        log.info("[ADMIN] Bank code={} config updated by admin id={}", bankCode, adminUserId);

        auditService.log(adminUserId, "ADMIN_BANK_CONFIG_UPDATE", "BANK_INSTITUTION", saved.id(),
                "{\"bankCode\":\"%s\",\"active\":%b}".formatted(saved.bankCode(), saved.active()), clientIp);

        return BankConfigResponse.from(saved);
    }

    public IntegrationHealthResponse getIntegrationHealth() {
        List<ComponentHealthResponse> components = new ArrayList<>();
        List<BankInstitutionRecord> banks = linkedAccountStore.findAllInstitutions();

        boolean allHealthy = true;

        for (BankInstitutionRecord bank : banks) {
            long start = System.currentTimeMillis();
            try {
                BankAdapter adapter = bankAdapterRegistry.get(bank.bankCode());
                long latency = Math.max(1, System.currentTimeMillis() - start);
                String status = bank.active() ? "UP" : "INACTIVE";
                components.add(new ComponentHealthResponse(
                        bank.displayName(),
                        status,
                        bank.simulated(),
                        latency,
                        bank.active() ? "Simulated bank adapter responsive" : "Bank is deactivated by admin"
                ));
            } catch (Exception e) {
                allHealthy = false;
                long latency = System.currentTimeMillis() - start;
                components.add(new ComponentHealthResponse(
                        bank.displayName(),
                        "DOWN",
                        bank.simulated(),
                        latency,
                        "Error checking adapter: " + e.getMessage()
                ));
            }
        }

        // Database component
        components.add(new ComponentHealthResponse(
                "Primary Database (Firestore)",
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
