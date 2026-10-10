package com.lankapay.identity.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Full payment profile returned from GET /api/v1/payment-profile/me.
 * Includes the user's own safe display name and verification status.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The authenticated user's full payment profile")
public record PaymentProfileMeResponse(

        @Schema(description = "Masked phone number", example = "+9477***4567")
        String maskedPhone,

        @Schema(description = "Whether phone is discoverable by others", example = "true")
        boolean discoverable,

        @Schema(description = "Phone verification status", example = "VERIFIED")
        String verificationStatus,

        @Schema(description = "Abbreviated display name shown to payees", example = "Kamal P.")
        String displayName
) {}
