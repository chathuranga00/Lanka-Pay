package com.lankapay.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.transfer.domain.IdempotencyRecord;
import com.lankapay.transfer.repository.IdempotencyRecordRepository;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Compute SHA-256 hash of a payload object.
     */
    public String hashPayload(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return IdempotencyRecord.hashPayload(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload for idempotency hashing", e);
        }
    }

    /**
     * Look up existing idempotency record and verify payload match.
     *
     * @param key         Client idempotency key
     * @param payloadHash SHA-256 hash of the incoming payload
     * @return Existing TransferResponse if previously processed, empty if new key
     * @throws ConflictException if the same key was used with a different payload
     */
    @Transactional(readOnly = true)
    public Optional<TransferResponse> findExistingResponse(String key, String payloadHash) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        Optional<IdempotencyRecord> existingOpt = repository.findByIdempotencyKey(key);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord existing = existingOpt.get();
        if (!existing.getPayloadHash().equals(payloadHash)) {
            log.warn("Idempotency conflict: key='{}' was used with different payload hash (stored={}, incoming={})",
                    key, existing.getPayloadHash(), payloadHash);
            throw new ConflictException("IDEMPOTENCY_CONFLICT",
                    "Idempotency key '%s' was already used with a different request payload".formatted(key));
        }

        if (existing.getResponseJson() != null) {
            try {
                TransferResponse response = objectMapper.readValue(existing.getResponseJson(), TransferResponse.class);
                log.info("Idempotency replay for key='{}', returning cached transfer id={}", key, existing.getTransferId());
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
            IdempotencyRecord record = new IdempotencyRecord(key, payloadHash, transferId, responseJson, httpStatus);
            repository.save(record);
            log.info("Saved idempotency record for key='{}', transferId={}", key, transferId);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate insert for idempotency key='{}', checking hash", key);
            // Verify existing
            repository.findByIdempotencyKey(key).ifPresent(existing -> {
                if (!existing.getPayloadHash().equals(payloadHash)) {
                    throw new ConflictException("IDEMPOTENCY_CONFLICT",
                            "Idempotency key '%s' was already used with a different request payload".formatted(key));
                }
            });
        }
    }
}
