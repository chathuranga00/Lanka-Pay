package com.lankapay.card.domain;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for cards and card consents.
 */
public interface CardStore {

    /**
     * Save or update a card.
     * Enforces optimistic locking via version and the unique constraint
     * of one active card per (userId, cardToken).
     */
    Card save(Card card);

    /**
     * Find card by ID regardless of owner.
     */
    Optional<Card> findById(Long id);

    /**
     * Find card by ID scoped to authenticated user (object-level authorization).
     */
    Optional<Card> findByIdAndUserId(Long id, Long userId);

    /**
     * Find all cards belonging to the user.
     */
    List<Card> findAllByUserId(Long userId);

    /**
     * Find any currently active or pending card for user and cardToken.
     */
    Optional<Card> findActiveByUserIdAndToken(Long userId, String cardToken);

    /**
     * Find active, frozen, or pending cards whose expYear and expMonth have passed.
     */
    List<Card> findCardsToProcessExpiration(int year, int month);

    /**
     * Record explicit user consent in the "consents" collection.
     */
    void saveConsent(Long userId, Long cardId, String consentType, boolean consentGiven, String ip);
}
