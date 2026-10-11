package com.lankapay.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.transfer.domain.IdempotencyRecordModel;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final TransactionStore transactionStore;
    private final ObjectMapper objectMapper;

    public IdempotencyService(TransactionStore transactionStore, ObjectMapper objectMapper) {
        this.transactionStore = transactionStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Compute SHA-256 hash of a payload object.
     */
    public String hashPayload(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return hashString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload for idempotency hashing", e);
        }
    }

    private static String hashString(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Look up existing idempotency record and verify payload match.
     */
    @Transactional(readOnly = true)
    public Optional<TransferResponse> findExistingResponse(String key, String payloadHash) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        var existingOpt = transactionStore.findIdempotencyRecord(key);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        var existing = existingOpt.get();
        if (!existing.payloadHash().equals(payloadHash)) {
            log.warn("Idempotency conflict: key='{}' was used with different payload hash (stored={}, incoming={})",
                    key, existing.payloadHash(), payloadHash);
            throw new ConflictException("IDEMPOTENCY_CONFLICT",
                    "Idempotency key '%s' was already used with a different request payload".formatted(key));
        }
        if (existing.responseJson() != null) {
            try {
                TransferResponse response = objectMapper.readValue(existing.responseJson(), TransferResponse.class);
                log.info("Idempotency replay for key='{}', returning cached transfer id={}", key, existing.transferId());
                return Optional.of(response);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached response for idempotency key='{}'", key, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Store the idempotency record and serialized response.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String key, String payloadHash, Long transferId, TransferResponse response, int httpStatus) {
        if (key == null || key.isBlank()) {
            return;
        }

        String responseJson = null;
        if (response != null) {
            try {
                responseJson = objectMapper.writeValueAsString(response);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize response for idempotency key='{}'", key, e);
            }
        }

        try {
            var record = new IdempotencyRecordModel(
                    null, key, payloadHash, transferId, responseJson, httpStatus, null
            );
            transactionStore.saveIdempotencyRecord(record);
            log.info("Saved idempotency record for key='{}', transferId={}", key, transferId);
        } catch (Exception ex) {
            log.warn("Concurrent duplicate insert for idempotency key='{}', checking hash", key);
            transactionStore.findIdempotencyRecord(key).ifPresent(existing -> {
                if (!existing.payloadHash().equals(payloadHash)) {
                    throw new ConflictException("IDEMPOTENCY_CONFLICT",
                            "Idempotency key '%s' was already used with a different request payload".formatted(key));
                }
            });
        }
    }
}
