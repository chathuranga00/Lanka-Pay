package com.lankapay.bill.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bill.adapter.BillValidationResult;
import com.lankapay.bill.adapter.BillerAdapter;
import com.lankapay.bill.adapter.BillerAdapterRegistry;
import com.lankapay.bill.adapter.BillerInfo;
import com.lankapay.bill.crypto.BillerReferenceEncryptor;
import com.lankapay.bill.domain.SavedBiller;
import com.lankapay.bill.domain.SavedBillerStore;
import com.lankapay.bill.web.dto.SavedBillerRequest;
import com.lankapay.bill.web.dto.SavedBillerResponse;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SavedBillerService {

    private static final Logger log = LoggerFactory.getLogger(SavedBillerService.class);

    private final SavedBillerStore savedBillerStore;
    private final BillerAdapterRegistry billerAdapterRegistry;
    private final BillerReferenceEncryptor encryptor;
    private final AuditService auditService;

    public SavedBillerService(
            SavedBillerStore savedBillerStore,
            BillerAdapterRegistry billerAdapterRegistry,
            BillerReferenceEncryptor encryptor,
            AuditService auditService
    ) {
        this.savedBillerStore = savedBillerStore;
        this.billerAdapterRegistry = billerAdapterRegistry;
        this.encryptor = encryptor;
        this.auditService = auditService;
    }

    public List<SavedBillerResponse> getSavedBillers(Long userId) {
        return savedBillerStore.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public SavedBillerResponse getSavedBillerById(Long id, Long userId) {
        SavedBiller biller = requireOwnedBiller(id, userId);
        return toResponse(biller);
    }

    public SavedBillerResponse saveBiller(Long userId, SavedBillerRequest request, String clientIp) {
        BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(request.billerId());
        BillerInfo info = adapter.getBiller(request.billerId())
                .orElseThrow(() -> new ResourceNotFoundException("BILLER_NOT_FOUND", "Biller not found: " + request.billerId()));

        // Validate customer reference with the adapter
        BillValidationResult validation = adapter.validateCustomerReference(info.id(), request.customerReference());
        if (!validation.valid()) {
            throw new ValidationException("INVALID_CUSTOMER_REFERENCE", validation.errorMessage());
        }

        String masked = BillerReferenceEncryptor.mask(request.customerReference());
        if (savedBillerStore.existsByUserIdAndBillerIdAndMaskedReference(userId, info.id(), masked)) {
            throw new ConflictException("BILLER_ALREADY_SAVED", "This biller reference is already saved");
        }

        String encrypted = encryptor.encrypt(request.customerReference().trim());

        SavedBiller saved = savedBillerStore.save(new SavedBiller(
                null,
                userId,
                info.id(),
                info.name(),
                info.category(),
                request.nickname().trim(),
                encrypted,
                masked,
                null,
                null
        ));

        auditService.log(userId, "SAVED_BILLER_CREATED", "SAVED_BILLER", saved.id(),
                "{\"billerId\":\"" + info.id() + "\"}", clientIp);
        log.info("Saved biller created userId={} billerId={} id={}", userId, info.id(), saved.id());
        return toResponse(saved);
    }

    public SavedBillerResponse updateBiller(Long id, Long userId, SavedBillerRequest request, String clientIp) {
        SavedBiller existing = requireOwnedBiller(id, userId);

        String newNickname = request.nickname() != null && !request.nickname().isBlank()
                ? request.nickname().trim()
                : existing.nickname();

        String encrypted = existing.encryptedReference();
        String masked = existing.maskedReference();

        if (request.customerReference() != null && !request.customerReference().isBlank()) {
            BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(existing.billerId());
            BillValidationResult validation = adapter.validateCustomerReference(existing.billerId(), request.customerReference());
            if (!validation.valid()) {
                throw new ValidationException("INVALID_CUSTOMER_REFERENCE", validation.errorMessage());
            }
            encrypted = encryptor.encrypt(request.customerReference().trim());
            masked = BillerReferenceEncryptor.mask(request.customerReference());
        }

        SavedBiller updated = savedBillerStore.save(new SavedBiller(
                existing.id(),
                userId,
                existing.billerId(),
                existing.billerName(),
                existing.category(),
                newNickname,
                encrypted,
                masked,
                existing.createdAt(),
                null
        ));

        auditService.log(userId, "SAVED_BILLER_UPDATED", "SAVED_BILLER", id, null, clientIp);
        log.info("Saved biller updated userId={} id={}", userId, id);
        return toResponse(updated);
    }

    public void deleteBiller(Long id, Long userId, String clientIp) {
        SavedBiller existing = requireOwnedBiller(id, userId);
        savedBillerStore.deleteById(existing.id());
        auditService.log(userId, "SAVED_BILLER_DELETED", "SAVED_BILLER", id, null, clientIp);
        log.info("Saved biller deleted userId={} id={}", userId, id);
    }

    /**
     * Resolves the plaintext reference for a saved biller owned by userId.
     */
    public String getDecryptedReference(Long id, Long userId) {
        SavedBiller biller = requireOwnedBiller(id, userId);
        return encryptor.decrypt(biller.encryptedReference());
    }

    private SavedBiller requireOwnedBiller(Long id, Long userId) {
        return savedBillerStore.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("SAVED_BILLER_NOT_FOUND",
                        "Saved biller with id " + id + " not found"));
    }

    private SavedBillerResponse toResponse(SavedBiller entity) {
        return new SavedBillerResponse(
                entity.id(),
                entity.billerId(),
                entity.billerName(),
                entity.category(),
                entity.nickname(),
                entity.maskedReference(),
                entity.createdAt()
        );
    }
}
