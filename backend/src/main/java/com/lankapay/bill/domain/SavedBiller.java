package com.lankapay.bill.domain;

import com.lankapay.bill.adapter.BillerCategory;

import java.time.Instant;

/**
 * Entity representing a user's saved biller.
 * Customer reference is stored encrypted.
 */
public record SavedBiller(
        Long id,
        Long userId,
        String billerId,
        String billerName,
        BillerCategory category,
        String nickname,
        String encryptedReference,
        String maskedReference,
        Instant createdAt,
        Instant updatedAt
) {
    public SavedBiller withNickname(String newNickname) {
        return new SavedBiller(id, userId, billerId, billerName, category, newNickname, encryptedReference, maskedReference, createdAt, Instant.now());
    }

    public SavedBiller withReference(String newEncrypted, String newMasked) {
        return new SavedBiller(id, userId, billerId, billerName, category, nickname, newEncrypted, newMasked, createdAt, Instant.now());
    }
}
