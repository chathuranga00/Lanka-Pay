package com.lankapay.transfer.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Idempotency key tracking for transfer requests.
 *
 * Table: trf_idempotency_keys
 *
 * Rules:
 * - Unique constraint on (idempotency_key) — DB-enforced.
 * - payload_hash is SHA-256 of the request payload; used to detect
 *   "same key, different payload" conflicts.
 * - response_json stores the serialised response for replay.
 * - Entries are immutable after creation.
 */
@Entity
@Table(name = "trf_idempotency_keys",
       indexes = @Index(name = "idx_trf_idem_key", columnList = "idempotency_key", unique = true))
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The client-supplied Idempotency-Key header value. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    /** SHA-256 hash of the request payload — for detecting payload mismatches. */
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    /** The transfer ID created for this key. */
    @Column(name = "transfer_id")
    private Long transferId;

    /** Cached serialised response for replay. */
    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    /** HTTP status code of the original response. */
    @Column(name = "http_status")
    private Integer httpStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String idempotencyKey, String payloadHash,
                             Long transferId, String responseJson, int httpStatus) {
        this.idempotencyKey = idempotencyKey;
        this.payloadHash    = payloadHash;
        this.transferId     = transferId;
        this.responseJson   = responseJson;
        this.httpStatus     = httpStatus;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long getId()               { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadHash()    { return payloadHash; }
    public Long getTransferId()       { return transferId; }
    public String getResponseJson()   { return responseJson; }
    public Integer getHttpStatus()    { return httpStatus; }
    public Instant getCreatedAt()     { return createdAt; }

    // ── Utility ────────────────────────────────────────────────

    /**
     * SHA-256 hash a payload string for comparison.
     */
    public static String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
