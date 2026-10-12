package com.lankapay.card.web.dto;

import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Masked card view returned across safe read and management endpoints.
 * Never leaks token, PAN, CVV, or PIN.
 */
@Schema(description = "Masked card response")
public record CardResponse(
        @Schema(description = "Internal card ID", example = "1001")
        Long id,

        @Schema(description = "User ID who owns this card", example = "1")
        Long userId,

        @Schema(description = "Linked bank account ID (if linked to an account)", example = "101")
        Long linkedBankAccountId,

        @Schema(description = "Last 4 digits of the card number", example = "0010")
        String last4,

        @Schema(description = "Expiration month (1-12)", example = "12")
        Integer expMonth,

        @Schema(description = "Expiration year", example = "2027")
        Integer expYear,

        @Schema(description = "Card brand label", example = "TEST CARD")
        String brandLabel,

        @Schema(description = "User nickname for the card", example = "My Shopping Card")
        String nickname,

        @Schema(description = "Kind of card: LINKED or VIRTUAL", example = "LINKED")
        CardKind cardKind,

        @Schema(description = "Current lifecycle status", example = "ACTIVE")
        CardStatus status,

        @Schema(description = "Whether online card transactions are currently enabled", example = "true")
        boolean onlinePaymentsEnabled,

        @Schema(description = "Per-transaction limit in LKR minor units", example = "5000000")
        Long perPaymentLimitMinor,

        @Schema(description = "Daily aggregate limit in LKR minor units", example = "20000000")
        Long dailyLimitMinor,

        @Schema(description = "Timestamp when the card was created in the system")
        Instant createdAt,

        @Schema(description = "Timestamp when the card was last updated")
        Instant updatedAt,

        @Schema(description = "Timestamp when card verification challenge succeeded")
        Instant verifiedAt,

        @Schema(description = "Whether this is a simulated test card", example = "true")
        boolean simulated
) {
    public static CardResponse from(Card card) {
        if (card == null) return null;
        return new CardResponse(
                card.id(),
                card.userId(),
                card.linkedBankAccountId(),
                card.last4(),
                card.expMonth(),
                card.expYear(),
                card.brandLabel(),
                card.nickname(),
                card.cardKind(),
                card.status(),
                card.onlinePaymentsEnabled(),
                card.perPaymentLimitMinor(),
                card.dailyLimitMinor(),
                card.createdAt(),
                card.updatedAt(),
                card.verifiedAt(),
                card.simulated()
        );
    }
}
