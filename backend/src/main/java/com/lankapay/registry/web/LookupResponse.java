package com.lankapay.registry.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.registry.domain.LookupResultType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response for GET /api/v1/payment-identities/lookup.
 *
 * Anti-enumeration design:
 * - HTTP 200 is returned ONLY for FOUND.
 * - All other result types return HTTP 404 with the resultType field set,
 *   so callers can differentiate UX states without revealing existence.
 * - The shape of non-FOUND responses is identical to prevent timing attacks
 *   on field presence.
 * - displayName and accounts are null for any non-FOUND result.
 *
 * Security: no full account numbers, no phone numbers, no NIC numbers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payment identity lookup response")
public record LookupResponse(

        @Schema(description = "Outcome of the lookup", example = "FOUND")
        LookupResultType resultType,

        /**
         * A safe display name derived from the payee's full name
         * (first name + last initial only, e.g. "Kamal P.").
         * Null for non-FOUND results.
         */
        @Schema(description = "Safe display name of the payee (first name + last initial)",
                example = "Kamal P.", nullable = true)
        String displayName,

        /**
         * Verification status of the payee's phone number.
         * Null for non-FOUND results.
         */
        @Schema(description = "Verification status", example = "VERIFIED", nullable = true)
        String verificationStatus,

        /**
         * Bank accounts the payee has allowed to be discoverable and can receive funds.
         * Empty list or null for non-FOUND results.
         */
        @Schema(description = "Payee's eligible receiving accounts (masked)", nullable = true)
        List<EligibleAccountSummary> accounts

) {
    // ── Factory helpers ────────────────────────────────────────

    public static LookupResponse found(String displayName, List<EligibleAccountSummary> accounts) {
        return new LookupResponse(LookupResultType.FOUND, displayName, "VERIFIED", accounts);
    }

    public static LookupResponse notFound() {
        return new LookupResponse(LookupResultType.NOT_FOUND, null, null, null);
    }

    public static LookupResponse discoveryDisabled() {
        return new LookupResponse(LookupResultType.DISCOVERY_DISABLED, null, null, null);
    }

    public static LookupResponse noEligibleAccount() {
        return new LookupResponse(LookupResultType.NO_ELIGIBLE_ACCOUNT, null, null, null);
    }

    public static LookupResponse privacyRestricted() {
        return new LookupResponse(LookupResultType.PRIVACY_RESTRICTED, null, null, null);
    }
}
